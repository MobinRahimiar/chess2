package com.chessmasterai

import org.junit.Assert.assertTrue
import org.junit.Test

class TacticalAnalyzerTest {
    @Test fun detectsCheckInSimplePosition() {
        // Black rook on e8 attacks the white king on e1.
        val board = MutableList(64) { '.' }
        board[60] = 'K'
        board[52] = 'r'
        board[0] = 'k'
        val state = ChessState(board, whiteToMove = true, castling = "")
        val findings = TacticalAnalyzer.analyze(state)
        assertTrue(findings.any { it.motif == TacticalMotif.CHECK })
    }

    @Test fun detectsKnightForkPattern() {
        // White knight c3 attacks black queen d5 and rook e4.
        val board = MutableList(64) { '.' }
        board[60] = 'K'
        board[4] = 'k'
        board[42] = 'N' // c3
        board[27] = 'q' // d5
        board[28] = 'r' // e5 (also attacked by the knight from c4? position adjusted below)
        // Put a second attacked valuable piece on e4 (square 36).
        board[28] = '.'
        board[36] = 'r'
        val state = ChessState(board, whiteToMove = true, castling = "")
        val findings = TacticalAnalyzer.analyze(state)
        assertTrue(findings.any { it.motif == TacticalMotif.FORK })
    }

    @Test fun initialPositionHasNoTacticalFindings() {
        assertTrue(TacticalAnalyzer.analyze(ChessEngine.initial()).isEmpty())
    }
}
