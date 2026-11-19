package com.chessmasterai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCoachTest {
    @Test fun replaysLegalPrincipalVariationAsNotation() {
        val state = ChessEngine.initial()
        val san = AiCoach.replayPv(state, listOf("e2e4", "e7e5", "g1f3"))
        assertEquals(listOf("e4", "e5", "Nf3"), san)
    }

    @Test fun coachComparesEngineCandidates() {
        val state = ChessEngine.initial()
        val report = EngineReport(
            listOf(
                EngineLine(1, 18, "+0.45", false, 1000, 50000, listOf("e2e4", "e7e5")),
                EngineLine(2, 18, "-1.30", false, 900, 45000, listOf("d2d4", "d7d5"))
            ), "e2e4", 18
        )
        val tips = AiCoach.insights(state, report)
        assertTrue(tips.any { it.contains("پیشنهاد اصلی") })
        assertTrue(tips.any { it.contains("به‌وضوح ترجیح") })
        assertTrue(tips.any { it.contains("ادامهٔ پیشنهادی") })
    }
}
