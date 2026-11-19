package com.chessmasterai

import org.junit.Assert.*
import org.junit.Test

class AnalysisExplainerTest {
    @Test fun convertsLegalUciMove() {
        val move = uciToMove(ChessEngine.initial(), "e2e4")
        assertNotNull(move)
        assertEquals("e4", ChessEngine.square(move!!.to))
    }

    @Test fun rejectsIllegalUciMove() {
        assertNull(uciToMove(ChessEngine.initial(), "e2e5"))
    }

    @Test fun explainsEvaluationFromEngineLine() {
        val line = EngineLine(1, 16, "+0.42", false, 1200, 30000, listOf("e2e4", "e7e5"))
        assertTrue(explainEngineLine(line).contains("به نفع سفید"))
        assertTrue(explainEngineLine(line).contains("e2e4"))
    }
}
