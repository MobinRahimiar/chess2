package com.chessmasterai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TacticalSearchTest {
    private fun san(state: ChessState, moves: List<ChessMove>) = moves.map { ChessEngine.notation(state, it) }

    // 1.f3 e5 2.g4 -- Black threatens ...Qh4#
    private val fastestMateThreat = "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 3"
    // Legal's trap, after 5.Nxe5 (Black to move; 5...Bxd1? loses to 6.Bxf7+ Ke7 7.Nd5#)
    private val legalAfterNxe5 = "rn1qkbnr/ppp2p1p/3p2p1/4N3/2B1P1b1/2N5/PPPP1PPP/R1BQK2R b KQkq - 0 5"
    // Same position after 5...Bxd1, White to move
    private val legalAfterBxd1 = "rn1qkbnr/ppp2p1p/3p2p1/4N3/2B1P3/2N5/PPPP1PPP/R1BbK2R w KQkq - 0 6"
    // Position before 5.Nxe5 (White to move)
    private val legalBeforeNxe5 = "rn1qkbnr/ppp2p1p/3p2p1/4p3/2B1P1b1/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 0 5"

    @Test fun matesInOneFindsFoolsMate() {
        val s = ChessEngine.fromFen("rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 3")
        assertEquals(listOf("Qh4"), san(s, TacticalSearch.matesInOne(s)))
    }

    @Test fun reportsVerifiedMateThreatForSideToMove() {
        val s = ChessEngine.fromFen(fastestMateThreat)
        val threats = TacticalSearch.mateThreats(s)
        assertEquals(1, threats.size)
        assertEquals(TacticalMotif.MATE_THREAT, threats[0].motif)
        assertTrue(threats[0].title.contains("Qh4"))
        // also surfaced by the main analyzer
        assertTrue(TacticalAnalyzer.analyze(s).any { it.motif == TacticalMotif.MATE_THREAT })
    }

    @Test fun noMateThreatInInitialPositionOrWhileInCheck() {
        assertTrue(TacticalSearch.mateThreats(ChessEngine.initial()).isEmpty())
        assertNull(TacticalSearch.nullMove(ChessEngine.fromFen("4k3/8/8/8/8/8/4r3/4K3 w - - 0 1")))
    }

    @Test fun findsForcedMateInTwoInLegalTrap() {
        val s = ChessEngine.fromFen(legalAfterBxd1)
        val found = TacticalSearch.mateInTwoFindings(s)
        assertTrue(found.any { it.motif == TacticalMotif.MATE_IN_TWO && it.title.contains("Bxf7") })
    }

    @Test fun mateInTwoNotReportedWhenMateInOneExists() {
        val s = ChessEngine.fromFen("7k/8/5KQ1/8/8/8/8/8 w - - 0 1")
        assertTrue(TacticalSearch.matesInOne(s).isNotEmpty())
        assertTrue(TacticalSearch.mateInTwoFindings(s).isEmpty())
    }

    @Test fun everyReportedMateInTwoReallyIsForced() {
        val s = ChessEngine.fromFen(legalAfterBxd1)
        for (m1 in TacticalSearch.matesInTwo(s)) {
            val a = ChessEngine.applyLegal(s, m1)
            val replies = ChessEngine.legalMoves(a)
            assertTrue(replies.isNotEmpty())
            for (r in replies) assertTrue(TacticalSearch.matesInOne(ChessEngine.applyLegal(a, r)).isNotEmpty())
        }
    }

    @Test fun flagsPoisonedCaptureBxd1() {
        val s = ChessEngine.fromFen(legalAfterNxe5)
        val f = TacticalSearch.poisonedCaptures(s)
        assertTrue(f.any { it.motif == TacticalMotif.POISONED_CAPTURE && it.title.contains("Bxd1") })
    }

    @Test fun safeCaptureIsNotFlaggedPoisoned() {
        // Plain position: White can take a hanging pawn with no mating consequences.
        val s = ChessEngine.fromFen("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1")
        assertTrue(TacticalSearch.poisonedCaptures(s).isEmpty())
    }

    @Test fun findsLegalTrapSetupNxe5() {
        val s = ChessEngine.fromFen(legalBeforeNxe5)
        val f = TacticalSearch.trapSetups(s)
        assertTrue(f.any { it.motif == TacticalMotif.TRAP_SETUP && it.title.contains("Nxe5") })
    }

    @Test fun quietPositionHasNoDeepFindings() {
        assertTrue(TacticalSearch.deepFindings(ChessEngine.initial()).isEmpty())
    }

    @Test fun deepFindingsOnlyUseLegalMoves() {
        for (fen in listOf(legalAfterNxe5, legalAfterBxd1, legalBeforeNxe5, fastestMateThreat)) {
            val s = ChessEngine.fromFen(fen)
            val legal = ChessEngine.legalMoves(s)
            for (f in TacticalSearch.deepFindings(s)) {
                assertTrue(legal.any { it.from == f.squares[0] && it.to == f.squares[1] })
            }
        }
    }

    private fun assertNull(x: Any?) = org.junit.Assert.assertNull(x)
}
