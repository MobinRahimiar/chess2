package com.chessmasterai

import java.util.concurrent.TimeUnit

/** Line-oriented channel to a UCI engine. Pure JVM so the whole protocol can be unit-tested. */
interface UciTransport {
    fun send(command: String)
    /** Next engine output line, null on timeout, [UciSession.EOF] once the engine output is closed. */
    fun nextLine(timeoutMs: Long): String?
}

data class UciTimeouts(
    val handshakeSeconds: Long = 20,
    val readySeconds: Long = 15,
    val analysisSeconds: Long = 180,
    val stopGraceSeconds: Long = 5
)

/**
 * One complete UCI analysis conversation: handshake, options, isready, position, go, collect.
 *
 * Safety properties (all unit-tested with a scripted engine):
 *  - FEN is validated by ChessEngine before anything is sent; a terminal position is rejected.
 *  - Only options the engine advertises are set.
 *  - A principal variation is shown only if every move replays legally from the root.
 *  - bound-only scores (lowerbound/upperbound) are not shown as exact evaluations.
 *  - `bestmove` must be legal at the root; engine death, CRITICAL ERROR and timeouts raise errors.
 *  - On analysis timeout `stop` is sent and the engine gets a grace period to return a bestmove.
 */
object UciSession {
    const val EOF = "\u0000UCI_EOF\u0000"

    fun analyze(
        transport: UciTransport,
        fen: String,
        settings: StockfishSettings,
        timeouts: UciTimeouts = UciTimeouts(),
        onStatus: (String) -> Unit = {},
        onReport: (EngineReport) -> Unit = {}
    ): EngineReport {
        val rootState = ChessEngine.fromFen(fen)
        require(ChessEngine.legalMoves(rootState).isNotEmpty()) {
            "The supplied FEN is a terminal position; Stockfish has no legal move to analyze"
        }

        transport.send("uci")
        val options = mutableSetOf<String>()
        var handshake = false
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeouts.handshakeSeconds)
        while (System.nanoTime() < deadline) {
            val line = transport.nextLine(200) ?: continue
            if (line == EOF) break
            if (line.startsWith("option name ")) options += line.removePrefix("option name ").substringBefore(" type ").trim()
            if (line.trim() == "uciok") { handshake = true; break }
        }
        if (!handshake) throw IllegalStateException("Stockfish UCI handshake timed out")

        fun setIfSupported(name: String, value: Int) { if (name in options) transport.send("setoption name $name value $value") }
        setIfSupported("Threads", settings.threads)
        setIfSupported("Hash", settings.hashMb)
        setIfSupported("MultiPV", settings.multiPv)
        transport.send("isready")
        if (!waitFor(transport, "readyok", timeouts.readySeconds)) throw IllegalStateException("Stockfish did not become ready")

        transport.send("ucinewgame")
        transport.send("position fen ${fen.trim()}")
        transport.send("go depth ${settings.depth}")
        onStatus("Stockfish analyzing at depth ${settings.depth}…")

        val lines = linkedMapOf<Int, EngineLine>()
        var bestMove = ""
        var reachedDepth = 0
        var stopSent = false
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeouts.analysisSeconds)
        while (true) {
            if (!stopSent && System.nanoTime() >= deadline) {
                transport.send("stop")
                stopSent = true
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeouts.stopGraceSeconds)
            } else if (stopSent && System.nanoTime() >= deadline) {
                throw IllegalStateException("Stockfish did not answer within the time limit")
            }
            val line = transport.nextLine(200) ?: continue
            if (line == EOF) throw IllegalStateException("Stockfish process ended unexpectedly")
            when {
                line.startsWith("info string CRITICAL ERROR") ->
                    throw IllegalStateException("Stockfish rejected the position or UCI command: $line")
                line.startsWith("info ") -> parseUciInfoLine(line)?.let { parsed ->
                    if (isLegalUciVariation(rootState, parsed.pv)) {
                        lines[parsed.rank] = parsed
                        reachedDepth = maxOf(reachedDepth, parsed.depth)
                        onReport(EngineReport(lines.values.sortedBy { it.rank }, "", reachedDepth))
                    }
                }
                line.startsWith("bestmove ") -> {
                    bestMove = line.split(Regex("\\s+")).getOrNull(1).orEmpty()
                    break
                }
            }
        }
        if (bestMove.isBlank() || bestMove == "(none)" || bestMove == "0000") {
            throw IllegalStateException("Stockfish returned no legal best move for this position")
        }
        if (uciToMove(rootState, bestMove) == null) {
            throw IllegalStateException("Stockfish returned a move that is illegal in the supplied position: $bestMove")
        }
        return EngineReport(lines.values.sortedBy { it.rank }, bestMove, reachedDepth)
    }

    private fun waitFor(t: UciTransport, expected: String, seconds: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (System.nanoTime() < deadline) {
            val line = t.nextLine(200) ?: continue
            if (line == EOF) return false
            if (line.trim() == expected) return true
            if (line.startsWith("info string CRITICAL ERROR")) return false
        }
        return false
    }

    private fun isLegalUciVariation(start: ChessState, pv: List<String>): Boolean {
        if (pv.isEmpty()) return false
        var position = start
        for (uci in pv) {
            val move = uciToMove(position, uci) ?: return false
            position = ChessEngine.applyLegal(position, move)
        }
        return true
    }
}
