package com.chessmasterai

/**
 * Verified, bounded tactical search built on the real rules engine.
 *
 * Every finding here is produced by enumerating legal moves ([ChessEngine.legalMoves])
 * and testing the resulting positions for checkmate. Nothing is estimated:
 *  - mate threat      : if the opponent were to move now, they would have a mate in one
 *  - mate in two      : a move after which EVERY legal reply allows a mate in one
 *  - poisoned capture : a legal capture after which the opponent has a forced mate in <= 2
 *  - trap setup       : a legal move that offers material whose capture allows a forced mate in <= 2
 *
 * Limits: mate-only. This does not evaluate material sacrifices that merely win material,
 * does not search deeper than mate in two, and a "trap setup" only works if the opponent
 * actually plays the poisoned capture; it is not a claim that the move is the best move.
 */
object TacticalSearch {
    private val pieceValue = mapOf('p' to 1, 'n' to 3, 'b' to 3, 'r' to 5, 'q' to 9)

    /** Hard cap on legalMoves() calls so a pathological position cannot hang the UI. */
    private const val NODE_BUDGET = 60_000

    class Budget { var used = 0; fun spend(): Boolean { used++; return used <= NODE_BUDGET } }

    private fun isCheckmate(s: ChessState): Boolean = ChessEngine.inCheck(s) && ChessEngine.legalMoves(s).isEmpty()

    /** Legal moves of the side to move that deliver checkmate immediately. */
    fun matesInOne(state: ChessState): List<ChessMove> =
        ChessEngine.legalMoves(state).filter { isCheckmate(ChessEngine.applyLegal(state, it)) }

    private fun hasMateInOne(state: ChessState): Boolean =
        ChessEngine.legalMoves(state).any { isCheckmate(ChessEngine.applyLegal(state, it)) }

    /** Same position with the other side to move. Not valid while the side to move is in check. */
    fun nullMove(state: ChessState): ChessState? =
        if (ChessEngine.inCheck(state)) null
        else state.copy(whiteToMove = !state.whiteToMove, enPassant = null)

    /** Moves for the side to move that mate in two: all replies allow a mate in one. */
    fun matesInTwo(state: ChessState): List<ChessMove> = matesInTwo(state, Budget())

    private fun matesInTwo(state: ChessState, budget: Budget): List<ChessMove> {
        val result = mutableListOf<ChessMove>()
        for (m1 in ChessEngine.legalMoves(state)) {
            if (!budget.spend()) break
            val afterOne = ChessEngine.applyLegal(state, m1)
            val replies = ChessEngine.legalMoves(afterOne)
            if (replies.isEmpty()) continue // mate in one or stalemate: not a "mate in two"
            var forced = true
            for (reply in replies) {
                if (!budget.spend()) { forced = false; break }
                if (!hasMateInOne(ChessEngine.applyLegal(afterOne, reply))) { forced = false; break }
            }
            if (forced) result += m1
        }
        return result
    }

    /** Opponent threatens to checkmate on their next move if nothing changes. */
    fun mateThreats(state: ChessState): List<TacticalFinding> {
        val flipped = nullMove(state) ?: return emptyList()
        val threats = matesInOne(flipped)
        if (threats.isEmpty()) return emptyList()
        val first = threats.first()
        val san = ChessEngine.notation(flipped, first)
        val extra = if (threats.size > 1) " (and ${threats.size - 1} other mating move(s))" else ""
        return listOf(TacticalFinding(
            "Mate threat: $san", TacticalMotif.MATE_THREAT,
            "If you make a neutral move, the opponent can play $san and checkmate$extra. Verified with the rules engine; find a move that removes it.",
            listOf(first.from, first.to), 5
        ))
    }

    /** Verified mates in two for the side to move. */
    fun mateInTwoFindings(state: ChessState, budget: Budget = Budget()): List<TacticalFinding> {
        if (matesInOne(state).isNotEmpty()) return emptyList() // mate in one is reported elsewhere
        return matesInTwo(state, budget).take(3).map { m ->
            val san = ChessEngine.notation(state, m)
            TacticalFinding(
                "Forced mate in two: $san", TacticalMotif.MATE_IN_TWO,
                "After $san every legal reply allows checkmate on the next move. Verified exhaustively with the rules engine.",
                listOf(m.from, m.to), 5
            )
        }
    }

    /** Captures that look free but allow the opponent a forced mate in at most two. */
    fun poisonedCaptures(state: ChessState, budget: Budget = Budget()): List<TacticalFinding> {
        val out = mutableListOf<TacticalFinding>()
        for (m in ChessEngine.legalMoves(state)) {
            val captured = if (m.enPassant) 'p' else state.board[m.to]
            if (captured == '.') continue
            if (!budget.spend()) break
            val after = ChessEngine.applyLegal(state, m)
            val mateOne = matesInOne(after)
            val depth = when {
                isCheckmate(after) -> continue // our capture mates: not poisoned
                mateOne.isNotEmpty() -> 1
                matesInTwo(after, budget).isNotEmpty() -> 2
                else -> continue
            }
            val san = ChessEngine.notation(state, m)
            out += TacticalFinding(
                "Poisoned capture: $san", TacticalMotif.POISONED_CAPTURE,
                "Capturing with $san allows the opponent a forced mate in $depth. Do not take material without checking this line.",
                listOf(m.from, m.to), 5
            )
        }
        return out.take(3)
    }

    /**
     * Legal moves that offer material (a capture of a piece worth >= 3 becomes available)
     * where that capture lets us force mate in at most two. Only works if the opponent
     * takes the bait; it says nothing about the move's value against best defence.
     */
    fun trapSetups(state: ChessState, budget: Budget = Budget()): List<TacticalFinding> {
        val out = mutableListOf<TacticalFinding>()
        for (m1 in ChessEngine.legalMoves(state)) {
            if (!budget.spend()) break
            val afterOne = ChessEngine.applyLegal(state, m1)
            if (ChessEngine.legalMoves(afterOne).isEmpty()) continue
            var bait: ChessMove? = null
            for (reply in ChessEngine.legalMoves(afterOne)) {
                val captured = if (reply.enPassant) 'p' else afterOne.board[reply.to]
                if ((pieceValue[captured.lowercaseChar()] ?: 0) < 3) continue
                if (!budget.spend()) break
                val afterBait = ChessEngine.applyLegal(afterOne, reply)
                if (isCheckmate(afterBait)) continue
                if (hasMateInOne(afterBait) || matesInTwo(afterBait, budget).isNotEmpty()) { bait = reply; break }
            }
            if (bait != null) {
                val san = ChessEngine.notation(state, m1)
                val baitSan = ChessEngine.notation(afterOne, bait)
                out += TacticalFinding(
                    "Trap setup: $san", TacticalMotif.TRAP_SETUP,
                    "After $san, if the opponent plays $baitSan, you have a forced mate (in at most two). It only works if they take the bait; check that $san is also sound against other replies.",
                    listOf(m1.from, m1.to, bait.from, bait.to), 3
                )
            }
        }
        return out.take(2)
    }

    /** All deep (search-based) findings. Run off the UI thread. */
    fun deepFindings(state: ChessState): List<TacticalFinding> {
        val budget = Budget()
        return mateInTwoFindings(state, budget) + poisonedCaptures(state, budget) + trapSetups(state, budget)
    }
}
