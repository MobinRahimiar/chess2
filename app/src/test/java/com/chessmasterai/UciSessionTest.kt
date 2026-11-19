package com.chessmasterai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue

/** In-memory scripted UCI engine: reacts to commands exactly like a real engine would, line by line. */
private class FakeEngine(
    private val options: List<String> = listOf("Threads", "Hash", "MultiPV"),
    private val onGo: (FakeEngine) -> Unit,
    private val answerUci: Boolean = true,
    private val answerStop: Boolean = false
) : UciTransport {
    val sent = mutableListOf<String>()
    private val out = LinkedBlockingQueue<String>()
    fun emit(line: String) { out.put(line) }
    override fun send(command: String) {
        sent += command
        when {
            command == "uci" && answerUci -> { options.forEach { emit("option name $it type spin default 1 min 1 max 512") }; emit("uciok") }
            command == "isready" -> emit("readyok")
            command.startsWith("go ") -> onGo(this)
            command == "stop" && answerStop -> emit("bestmove e2e4")
        }
    }
    override fun nextLine(timeoutMs: Long): String? = out.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
}

class UciSessionTest {
    private val start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    private val settings = StockfishSettings(threads = 2, hashMb = 64, multiPv = 2, depth = 10)
    private val fast = UciTimeouts(handshakeSeconds = 1, readySeconds = 1, analysisSeconds = 1, stopGraceSeconds = 1)

    @Test fun fullConversationProducesRankedLegalLines() {
        val engine = FakeEngine(onGo = {
            it.emit("info depth 10 multipv 1 score cp 35 nodes 1000 nps 500000 pv e2e4 e7e5 g1f3")
            it.emit("info depth 10 multipv 2 score cp 20 nodes 1000 nps 500000 pv d2d4 d7d5")
            it.emit("bestmove e2e4 ponder e7e5")
        })
        val report = UciSession.analyze(engine, start, settings, fast)
        assertEquals("e2e4", report.bestMove)
        assertEquals(listOf(1, 2), report.lines.map { it.rank })
        assertEquals(10, report.depth)
        // protocol order and applied options
        assertEquals(listOf("uci", "setoption name Threads value 2", "setoption name Hash value 64",
            "setoption name MultiPV value 2", "isready", "ucinewgame", "position fen $start", "go depth 10"), engine.sent)
    }

    @Test fun onlyAdvertisedOptionsAreSet() {
        val engine = FakeEngine(options = listOf("Hash"), onGo = { it.emit("bestmove e2e4") })
        UciSession.analyze(engine, start, settings, fast)
        assertTrue(engine.sent.none { it.contains("Threads") || it.contains("MultiPV") })
        assertTrue(engine.sent.contains("setoption name Hash value 64"))
    }

    @Test fun illegalPrincipalVariationIsDropped() {
        val engine = FakeEngine(onGo = {
            it.emit("info depth 10 multipv 1 score cp 35 pv e2e5")          // illegal first move
            it.emit("info depth 10 multipv 2 score cp 20 pv d2d4 d4d5")      // second move illegal
            it.emit("info depth 10 multipv 1 score cp 30 pv e2e4")
            it.emit("bestmove e2e4")
        })
        val report = UciSession.analyze(engine, start, settings, fast)
        assertEquals(1, report.lines.size)
        assertEquals(listOf("e2e4"), report.lines[0].pv)
    }

    @Test fun boundScoresAreNotShownAsExact() {
        val engine = FakeEngine(onGo = {
            it.emit("info depth 12 multipv 1 score cp 99 lowerbound pv e2e4")
            it.emit("bestmove e2e4")
        })
        assertTrue(UciSession.analyze(engine, start, settings, fast).lines.isEmpty())
    }

    @Test fun illegalBestMoveIsRejected() {
        val engine = FakeEngine(onGo = { it.emit("bestmove e2e5") })
        expectError("illegal") { UciSession.analyze(engine, start, settings, fast) }
    }

    @Test fun noBestMoveIsRejected() {
        val engine = FakeEngine(onGo = { it.emit("bestmove (none)") })
        expectError("no legal best move") { UciSession.analyze(engine, start, settings, fast) }
    }

    @Test fun criticalErrorFromEngineIsReported() {
        val engine = FakeEngine(onGo = { it.emit("info string CRITICAL ERROR Illegal position") })
        expectError("rejected") { UciSession.analyze(engine, start, settings, fast) }
    }

    @Test fun engineDeathDuringSearchIsReported() {
        val engine = FakeEngine(onGo = { it.emit(UciSession.EOF) })
        expectError("ended unexpectedly") { UciSession.analyze(engine, start, settings, fast) }
    }

    @Test fun silentEngineTimesOutOnHandshake() {
        val engine = FakeEngine(answerUci = false, onGo = {})
        expectError("handshake") { UciSession.analyze(engine, start, settings, fast) }
    }

    @Test fun analysisTimeoutSendsStopAndAcceptsLateBestMove() {
        val engine = FakeEngine(onGo = {}, answerStop = true)
        val report = UciSession.analyze(engine, start, settings, fast)
        assertTrue(engine.sent.contains("stop"))
        assertEquals("e2e4", report.bestMove)
    }

    @Test fun analysisTimeoutWithoutAnyAnswerFails() {
        val engine = FakeEngine(onGo = {}, answerStop = false)
        expectError("time limit") { UciSession.analyze(engine, start, settings, fast) }
    }

    @Test fun invalidAndTerminalFensNeverReachTheEngine() {
        val engine = FakeEngine(onGo = {})
        try { UciSession.analyze(engine, "not a fen", settings, fast); fail() } catch (_: IllegalArgumentException) { }
        try { UciSession.analyze(engine, "7k/6Q1/6K1/8/8/8/8/8 b - - 0 1", settings, fast); fail() } catch (_: IllegalArgumentException) { }
        assertTrue(engine.sent.isEmpty())
    }

    private fun expectError(fragment: String, block: () -> Unit) {
        try { block(); fail("expected error containing '$fragment'") }
        catch (e: IllegalStateException) { assertTrue(e.message, e.message!!.contains(fragment)) }
    }
}
