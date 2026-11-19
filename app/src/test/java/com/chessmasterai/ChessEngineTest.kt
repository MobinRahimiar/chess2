package com.chessmasterai

import org.junit.Assert.*
import org.junit.Test

class ChessEngineTest {
    private fun play(s: ChessState, from: String, to: String): ChessState {
        val f = (8 - from[1].digitToInt()) * 8 + (from[0] - 'a')
        val t = (8 - to[1].digitToInt()) * 8 + (to[0] - 'a')
        val move = ChessEngine.legalMoves(s).firstOrNull { it.from == f && it.to == t && it.promotion == null }
        return ChessEngine.apply(s, move ?: error("Illegal test move: $from$to"))!!
    }

    @Test fun initialPositionHasTwentyLegalMoves() {
        assertEquals(20, ChessEngine.legalMoves(ChessEngine.initial()).size)
    }

    @Test fun openingMovesUpdateTurnAndFen() {
        var s = ChessEngine.initial()
        s = play(s, "e2", "e4")
        assertFalse(s.whiteToMove)
        assertEquals("e3", s.enPassant?.let(ChessEngine::square))
        s = play(s, "e7", "e5")
        assertTrue(s.whiteToMove)
        assertEquals(2, s.fullmove)
    }

    @Test fun foolsMateIsDetectedAsCheckmate() {
        var s = ChessEngine.initial()
        s = play(s,"f2","f3"); s = play(s,"e7","e5")
        s = play(s,"g2","g4"); s = play(s,"d8","h4")
        assertEquals(GameResult.CHECKMATE_BLACK, ChessEngine.result(s))
    }

    @Test fun fenRoundTripsForInitialAndMidgamePositions() {
        var s = ChessEngine.initial()
        s = play(s, "e2", "e4")
        s = play(s, "c7", "c5")
        assertEquals(s, ChessEngine.fromFen(ChessEngine.fen(s)))
        assertEquals(ChessEngine.initial(), ChessEngine.fromFen(ChessEngine.fen(ChessEngine.initial())))
    }

    @Test fun malformedFenIsRejected() {
        try {
            ChessEngine.fromFen("not-a-fen")
            fail("Expected invalid FEN to be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun illegalMoveCannotBeApplied() {
        val s = ChessEngine.initial()
        val illegal = ChessMove(52, 36) // e2-e4 is legal; use e2-e5 instead
        assertNull(ChessEngine.apply(s, illegal.copy(to = 28)))
    }
}
