package com.chessmasterai

import kotlin.math.abs

/** A locally searched principal variation. Scores are centipawns from the root side's view. */
data class PredictedLine(
    val rank: Int,
    val moves: List<ChessMove>,
    val san: List<String>,
    val scoreCp: Int,
    val isMate: Boolean,
    val nodes: Long
) {
    val firstMove: ChessMove? get() = moves.firstOrNull()
    /** The opponent's best reply inside this line (candidate response, not a certainty). */
    val opponentReply: ChessMove? get() = moves.getOrNull(1)
    val opponentReplySan: String? get() = san.getOrNull(1)
    val continuation: String get() = san.joinToString(" ")
}

/** What the opponent would play if the side to move "passed" (null-move search). */
data class PredictedThreat(
    val move: ChessMove,
    val san: String,
    val gainCp: Int,
    val isMate: Boolean,
    val description: String
)

data class PredictionReport(
    val lines: List<PredictedLine>,
    val searchedDepthPlies: Int,
    val nodes: Long,
    val truncated: Boolean,
    val explanation: String,
    val threat: PredictedThreat? = null
)

/**
 * Bounded local search: iterative deepening, alpha-beta, quiescence on captures.
 *
 * - Every branch comes from ChessEngine.legalMoves, so no illegal move can be emitted.
 * - Each root move is searched with a FULL window, so ranks 2..N carry exact scores for the
 *   searched depth (not alpha-beta bounds) and all root moves share the node budget fairly.
 * - Iterative deepening: the report always comes from the deepest FULLY completed iteration.
 * - The evaluator (material, pawn advance, minor-piece centralisation, mobility) is light
 *   guidance, not Stockfish. Opponent replies are candidate responses, never probabilities.
 */
object PredictionEngine {
    private const val MATE = 100_000
    private const val INF = 1_000_000
    private const val DEFAULT_NODE_LIMIT = 45_000L
    private const val QUIESCENCE_DEPTH = 4
    private const val THREAT_MIN_GAIN_CP = 150
    private val values = mapOf('p' to 100, 'n' to 320, 'b' to 330, 'r' to 500, 'q' to 900, 'k' to 0)

    private data class SearchResult(val score: Int, val pv: List<ChessMove>)
    private data class Entry(val depth: Int, val best: ChessMove?)
    private class Ctx(val hardLimit: Long) {
        var nodes = 0L
        var limit = hardLimit      // current slice limit
        var truncated = false      // slice limit reached
        val table = HashMap<String, Entry>()
        fun exhausted() = nodes >= limit || Thread.currentThread().isInterrupted
    }
    private class RootIteration(val results: List<Pair<ChessMove, SearchResult>>, val complete: Boolean)

    fun predict(
        state: ChessState,
        depthPlies: Int = 4,
        maxLines: Int = 5,
        nodeLimit: Long = DEFAULT_NODE_LIMIT
    ): PredictionReport {
        require(depthPlies in 1..5) { "Search depth must be between 1 and 5 plies" }
        require(maxLines in 1..5) { "Line count must be between 1 and 5" }
        require(nodeLimit >= 100) { "Node limit is too small" }
        val legal = ChessEngine.legalMoves(state)
        if (legal.isEmpty()) return PredictionReport(emptyList(), depthPlies, 0, false, "No legal moves: the position is terminal.")

        val mainLimit = if (nodeLimit >= 400) nodeLimit * 3 / 4 else nodeLimit
        val ctx = Ctx(mainLimit)
        var best: RootIteration? = null
        var doneDepth = 0
        for (d in 1..depthPlies) {
            val iteration = searchRoot(state, legal, d, ctx)
            if (iteration.complete || best == null) {
                best = iteration; doneDepth = if (iteration.complete) d else doneDepth
            }
            if (!iteration.complete || ctx.nodes >= ctx.hardLimit) break
            // a forced mate found: deeper search cannot improve the top line
            if (iteration.results.maxOf { it.second.score } >= MATE - 100) break
        }
        val results = best?.results.orEmpty()
        val ranked = results.sortedWith(
            compareByDescending<Pair<ChessMove, SearchResult>> { it.second.score }.thenBy { it.second.pv.size }
        ).take(maxLines)
        val lines = ranked.mapIndexed { index, (_, result) ->
            val san = mutableListOf<String>()
            val moves = mutableListOf<ChessMove>()
            var cursor = state
            for (move in result.pv) {
                if (move !in ChessEngine.legalMoves(cursor)) break
                san += ChessEngine.notation(cursor, move); moves += move
                cursor = ChessEngine.applyLegal(cursor, move)
            }
            PredictedLine(index + 1, moves, san, result.score, abs(result.score) >= MATE - 100, ctx.nodes)
        }
        val truncated = doneDepth < depthPlies
        val bestScore = ranked.firstOrNull()?.second?.score ?: 0
        val threat = if (lines.isEmpty()) null else findThreat(state, bestScore, minOf(3, maxOf(1, doneDepth)), (nodeLimit - mainLimit).coerceAtLeast(100))
        val note = if (truncated)
            "Node budget reached: lines come from a completed ${doneDepth}-ply search (requested $depthPlies). Opponent replies are candidates, not certainties."
        else "Local search completed to $doneDepth plies. Opponent replies are candidate responses, not probabilities or certainties."
        return PredictionReport(lines, doneDepth.coerceAtLeast(1), ctx.nodes, truncated, note, threat)
    }

    private fun searchRoot(state: ChessState, legal: List<ChessMove>, depth: Int, ctx: Ctx): RootIteration {
        val ordered = orderMoves(state, legal, ctx.table[ChessEngine.positionKey(state)]?.best)
        val out = mutableListOf<Pair<ChessMove, SearchResult>>()
        var complete = true
        val iterationEnd = ctx.hardLimit
        for ((i, move) in ordered.withIndex()) {
            val remainingMoves = ordered.size - i
            val share = ((iterationEnd - ctx.nodes) / remainingMoves).coerceAtLeast(1)
            ctx.limit = ctx.nodes + share
            ctx.truncated = false
            val child = ChessEngine.applyLegal(state, move)
            val r = search(child, depth - 1, -INF, INF, ctx, 1)
            if (ctx.truncated || Thread.currentThread().isInterrupted) { complete = false; break }
            out += move to SearchResult(-r.score, listOf(move) + r.pv)
        }
        if (complete && out.isNotEmpty()) {
            ctx.table[ChessEngine.positionKey(state)] = Entry(depth, out.maxByOrNull { it.second.score }?.first)
        }
        return RootIteration(out, complete)
    }

    private fun findThreat(state: ChessState, ownBestScore: Int, depth: Int, budget: Long): PredictedThreat? {
        val passed = TacticalSearch.nullMove(state) ?: return null
        val legal = ChessEngine.legalMoves(passed)
        if (legal.isEmpty()) return null
        val ctx = Ctx(budget)
        var last: RootIteration? = null
        for (d in 1..depth) {
            val iteration = searchRoot(passed, legal, d, ctx)
            if (!iteration.complete) break
            last = iteration
            if (iteration.results.maxOf { r -> r.second.score } >= MATE - 100) break
        }
        val top = last?.results?.maxByOrNull { r -> r.second.score } ?: return null
        val oppScoreIfPass = top.second.score
        val gain = oppScoreIfPass + ownBestScore // how much better the opponent does if we ignore them
        val isMate = oppScoreIfPass >= MATE - 100
        if (!isMate && gain < THREAT_MIN_GAIN_CP) return null
        val san = ChessEngine.notation(passed, top.first)
        return PredictedThreat(
            top.first, san, gain, isMate,
            if (isMate) "If you ignore the opponent, $san starts a forced mate within the searched depth."
            else "If you ignore the opponent, $san improves their evaluation by about ${"%.1f".format(gain / 100.0)} pawns versus your best move."
        )
    }

    private fun search(state: ChessState, depth: Int, alphaIn: Int, beta: Int, ctx: Ctx, ply: Int): SearchResult {
        ctx.nodes++
        if (ctx.exhausted()) { ctx.truncated = true; return SearchResult(0, emptyList()) }
        val legal = ChessEngine.legalMoves(state)
        if (legal.isEmpty()) {
            return if (ChessEngine.inCheck(state)) SearchResult(-MATE + ply, emptyList()) else SearchResult(0, emptyList())
        }
        if (depth <= 0) return SearchResult(quiesce(state, legal, alphaIn, beta, ctx, ply, QUIESCENCE_DEPTH), emptyList())

        val key = ChessEngine.positionKey(state)
        val ordered = orderMoves(state, legal, ctx.table[key]?.best)
        var alpha = alphaIn
        var bestScore = -INF
        var bestMove: ChessMove? = null
        var bestPv: List<ChessMove> = emptyList()
        for (move in ordered) {
            val result = search(ChessEngine.applyLegal(state, move), depth - 1, -beta, -alpha, ctx, ply + 1)
            if (ctx.truncated) return SearchResult(0, emptyList())
            val score = -result.score
            if (score > bestScore) { bestScore = score; bestMove = move; bestPv = listOf(move) + result.pv }
            if (score > alpha) alpha = score
            if (alpha >= beta) break
        }
        if (bestMove != null) ctx.table[key] = Entry(depth, bestMove)
        return SearchResult(bestScore, bestPv)
    }

    /** Captures/promotions only (all evasions when in check), so material is not mis-scored mid-exchange. */
    private fun quiesce(state: ChessState, legal: List<ChessMove>, alphaIn: Int, beta: Int, ctx: Ctx, ply: Int, qDepth: Int): Int {
        val inCheck = ChessEngine.inCheck(state)
        val stand = evaluate(state, legal.size)
        if (qDepth <= 0) return stand
        var alpha = alphaIn
        if (!inCheck) {
            if (stand >= beta) return stand
            if (stand > alpha) alpha = stand
        }
        val candidates = if (inCheck) legal else legal.filter { it.enPassant || state.board[it.to] != '.' || it.promotion != null }
        var best = if (inCheck) -INF else stand
        for (move in orderMoves(state, candidates)) {
            ctx.nodes++
            if (ctx.exhausted()) { ctx.truncated = true; return best.coerceAtLeast(-INF + 1) }
            val child = ChessEngine.applyLegal(state, move)
            val childLegal = ChessEngine.legalMoves(child)
            val score = if (childLegal.isEmpty()) {
                if (ChessEngine.inCheck(child)) MATE - ply else 0
            } else -quiesce(child, childLegal, -beta, -alpha, ctx, ply + 1, qDepth - 1)
            if (score > best) best = score
            if (score > alpha) alpha = score
            if (alpha >= beta) break
        }
        return if (best == -INF) -MATE + ply else best
    }

    private fun evaluate(state: ChessState, mobilityCount: Int): Int {
        var score = 0
        for (i in 0..63) {
            val p = state.board[i]
            if (p == '.') continue
            val sign = if (p.isUpperCase()) 1 else -1
            val type = p.lowercaseChar()
            var value = values[type] ?: 0
            if (type == 'p') {
                val advance = if (sign == 1) 6 - i / 8 else i / 8 - 1
                value += advance.coerceAtLeast(0) * 7
            }
            if (type == 'n' || type == 'b') {
                val centerDistance = abs(3.5 - i % 8) + abs(3.5 - i / 8)
                value += ((7.0 - centerDistance) * 3).toInt()
            }
            score += sign * value
        }
        score += if (state.whiteToMove) mobilityCount.coerceAtMost(40) else -mobilityCount.coerceAtMost(40)
        return if (state.whiteToMove) score else -score
    }

    private fun orderMoves(state: ChessState, moves: List<ChessMove>, preferred: ChessMove? = null): List<ChessMove> =
        moves.sortedByDescending { move ->
            val moving = state.board[move.from].lowercaseChar()
            val captured = if (move.enPassant) 'p' else state.board[move.to].lowercaseChar()
            (if (move == preferred) 100_000 else 0) +
                (if (captured != '.') 10_000 + (values[captured] ?: 0) - (values[moving] ?: 0) / 10 else 0) +
                (if (move.promotion != null) 8_000 + (values[move.promotion.lowercaseChar()] ?: 0) else 0) +
                (if (move.castle) 100 else 0)
        }
}
