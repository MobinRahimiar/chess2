package com.chessmasterai

import org.junit.Assert.assertEquals
import org.junit.Test

class OpeningExplorerHistoryTest {
    @Test fun convertsPlayedHistoryToUciCoordinates() {
        val history = mutableListOf(ChessEngine.initial())
        listOf("e4", "e5", "Nf3", "Nc6").forEach { san ->
            val state = history.last()
            val move = ChessEngine.legalMoves(state).first { ChessEngine.notation(state, it) == san }
            history += ChessEngine.apply(state, move)!!
        }
        assertEquals(listOf("e2e4", "e7e5", "g1f3", "b8c6"), OpeningExplorerClient.historyToUci(history))
    }

    @Test fun emptyHistoryHasNoMoves() {
        assertEquals(emptyList<String>(), OpeningExplorerClient.historyToUci(listOf(ChessEngine.initial())))
    }
}
