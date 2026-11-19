package com.chessmasterai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionEngineTest {
    @Test fun returnsSeveralLegalCandidateLinesFromInitialPosition() {
        val state = ChessEngine.initial()
        val report = PredictionEngine.predict(state, depthPlies = 2, maxLines = 5, nodeLimit = 20_000)
        assertTrue(report.lines.isNotEmpty())
        assertTrue(report.lines.size <= 5)
        report.lines.forEach { line ->
            val first = line.firstMove
            assertNotNull(first)
            assertTrue(ChessEngine.legalMoves(state).contains(first))
            assertTrue(line.moves.isNotEmpty())
            assertEquals(line.moves.size, line.san.size)
        }
    }

    @Test fun respectsRequestedLineLimitAndDepthRange() {
        val report = PredictionEngine.predict(ChessEngine.initial(), depthPlies = 1, maxLines = 3, nodeLimit = 10_000)
        assertTrue(report.lines.size <= 3)
        assertEquals(1, report.searchedDepthPlies)
    }

    @Test fun terminalPositionReturnsNoCandidateMoves() {
        val state = ChessEngine.fromFen("7k/6Q1/6K1/8/8/8/8/8 b - - 0 1")
        val report = PredictionEngine.predict(state, depthPlies = 2)
        assertTrue(report.lines.isEmpty())
        assertFalse(report.explanation.isBlank())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDepthOutsideSupportedRange() {
        PredictionEngine.predict(ChessEngine.initial(), depthPlies = 6)
    }

    @Test fun linesAreSortedAndAllRootMovesSearchedWithinSmallBudget() {
        val report = PredictionEngine.predict(ChessEngine.initial(), depthPlies = 4, maxLines = 5, nodeLimit = 3_000)
        assertEquals(5, report.lines.size)
        assertEquals(report.lines.map { it.scoreCp }.sortedDescending(), report.lines.map { it.scoreCp })
    }

    @Test fun everyPrincipalVariationReplaysLegally() {
        val start = ChessEngine.fromFen("r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3")
        val report = PredictionEngine.predict(start, depthPlies = 3, maxLines = 5, nodeLimit = 30_000)
        for (line in report.lines) {
            var cursor = start
            for (m in line.moves) {
                assertTrue(ChessEngine.legalMoves(cursor).contains(m))
                cursor = ChessEngine.applyLegal(cursor, m)
            }
        }
    }

    @Test fun takesAFreeQueenAndRanksItFirst() {
        // White rook on a1 can capture an undefended black queen on a8.
        val s = ChessEngine.fromFen("q3k3/8/8/8/8/8/8/R3K3 w - - 0 1")
        val report = PredictionEngine.predict(s, depthPlies = 2, maxLines = 3, nodeLimit = 20_000)
        assertEquals("Rxa8+", report.lines[0].san[0].let { if (it == "Rxa8") "Rxa8+" else it })
        assertTrue(report.lines[0].scoreCp > report.lines[1].scoreCp + 500)
    }

    @Test fun findsForcedMateAndFlagsIt() {
        val s = ChessEngine.fromFen("6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1")
        val report = PredictionEngine.predict(s, depthPlies = 2, maxLines = 3, nodeLimit = 20_000)
        val top = report.lines[0]
        assertTrue(top.isMate)
        assertEquals("Ra8", top.san[0])
    }

    @Test fun quiescenceAvoidsPoisonedPawnGrab() {
        // Black queen takes a pawn defended by a bishop: at 1 ply a plain evaluator would like it.
        val s = ChessEngine.fromFen("4k3/8/8/3q4/8/2B5/4P3/4K3 b - - 0 1")
        val report = PredictionEngine.predict(s, depthPlies = 1, maxLines = 5, nodeLimit = 20_000)
        assertTrue(report.lines.none { it.san[0] == "Qxc3+" && it.scoreCp > 0 })
    }

    @Test fun reportsOpponentReplyInsideLine() {
        val report = PredictionEngine.predict(ChessEngine.initial(), depthPlies = 3, maxLines = 3, nodeLimit = 20_000)
        assertTrue(report.lines.all { it.opponentReply != null && it.opponentReplySan != null })
    }

    @Test fun reportsOpponentThreatWhenIgnoredButNotInQuietPosition() {
        // Black threatens ...Qh4# after 1.f3 e5 2.g4 (White to move).
        val s = ChessEngine.fromFen("rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 3")
        val threat = PredictionEngine.predict(s, depthPlies = 3, maxLines = 3, nodeLimit = 40_000).threat
        assertNotNull(threat)
        assertEquals("Qh4", threat!!.san)
        assertTrue(threat.isMate)
        assertEquals(null, PredictionEngine.predict(ChessEngine.initial(), depthPlies = 2, nodeLimit = 20_000).threat)
    }
}
