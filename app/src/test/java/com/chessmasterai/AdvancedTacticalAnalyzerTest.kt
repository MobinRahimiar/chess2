package com.chessmasterai

import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedTacticalAnalyzerTest {
    @Test fun detectsLegalCheckingCandidates() {
        val board = MutableList(64) { '.' }
        board[56] = 'K'; board[7] = 'k'; board[59] = 'Q'; board[3] = 'r'
        val state = ChessState(board, whiteToMove = true, castling = "")
        val findings = TacticalAnalyzer.analyze(state)
        assertTrue(findings.any { it.motif == TacticalMotif.FORCING_MOVE && it.title.contains("Forcing candidate") })
    }

    @Test fun detectsAdvancedPassedPawnPromotionThreatSignal() {
        val board = MutableList(64) { '.' }
        board[60] = 'K'; board[4] = 'k'; board[8] = 'P'
        val state = ChessState(board, whiteToMove = true, castling = "")
        assertTrue(TacticalAnalyzer.analyze(state).any { it.motif == TacticalMotif.PROMOTION_THREAT })
    }


    @Test fun doesNotReportRookPinOnDiagonal() {
        val board = MutableList(64) { '.' }
        board[60] = 'K' // e1
        board[51] = 'N' // d2
        board[33] = 'r' // b4: diagonal to the king, not a rook line
        board[0] = 'k'
        val findings = TacticalAnalyzer.analyze(ChessState(board, whiteToMove = true, castling = ""))
        assertTrue(findings.none { it.motif == TacticalMotif.PIN })
    }

    @Test fun findsVerifiedMateInOneFromLegalMoves() {
        val board = MutableList(64) { '.' }
        board[21] = 'K' // f6
        board[22] = 'Q' // g6
        board[7] = 'k'  // h8
        val findings = TacticalAnalyzer.analyze(ChessState(board, whiteToMove = true, castling = ""))
        assertTrue(findings.any { it.motif == TacticalMotif.MATE_IN_ONE && it.title.contains("Qg7") })
    }

    @Test fun keepsInitialPositionQuiet() {
        assertTrue(TacticalAnalyzer.analyze(ChessEngine.initial()).isEmpty())
    }
}
