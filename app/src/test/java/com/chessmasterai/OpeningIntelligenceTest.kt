package com.chessmasterai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningIntelligenceTest {
    @Test fun startingPositionOffersLegalBookMoves() {
        val suggestions = OpeningIntelligence.suggest(ChessEngine.initial(), emptyList())
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.all { it.move != null && it.move in ChessEngine.legalMoves(ChessEngine.initial()) })
        assertTrue(suggestions.any { it.san == "e4" })
    }

    @Test fun italianGameContinuesWithBishopDevelopment() {
        val state = listOf("e4", "e5", "Nf3", "Nc6").fold(ChessEngine.initial()) { current, san ->
            val move = ChessEngine.legalMoves(current).first { ChessEngine.notation(current, it) == san }
            ChessEngine.apply(current, move)!!
        }
        val suggestions = OpeningIntelligence.suggest(state, listOf("1. e4", "1... e5", "2. Nf3", "2... Nc6"))
        assertTrue(suggestions.any { it.san == "Bc4" && it.openingName == "Italian Game" })
    }

    @Test fun notationParserRemovesMoveNumbersAndCheckSymbols() {
        assertEquals(listOf("e4", "e5", "Nf3"), OpeningIntelligence.movesFromNotation(listOf("1. e4", "1... e5", "2. Nf3+")))
    }

    @Test fun expandedBookCarriesEcoMetadata() {
        assertTrue(OpeningIntelligence.lines.size >= 45)
        assertTrue(OpeningIntelligence.lines.all { it.eco.matches(Regex("[A-E][0-9]{2}")) })
        val suggestion = OpeningIntelligence.suggest(ChessEngine.initial(), emptyList())
            .first { it.san == "e4" }
        assertEquals("B00", suggestion.eco)
    }

    @Test fun localFrequencyOnlyCountsGamesWithExactPrefix() {
        val games = listOf(
            SavedChessGame("1", "Italian", 1L, "Imported", emptyList(), listOf("e4", "e5", "Nf3", "Nc6", "Bc4")),
            SavedChessGame("2", "Sicilian", 2L, "Imported", emptyList(), listOf("e4", "c5", "Nf3", "Nc6", "Bc4")),
            SavedChessGame("3", "French", 3L, "Imported", emptyList(), listOf("e4", "e6", "d4", "d5", "Bc4"))
        )
        val state = listOf("e4", "e5", "Nf3", "Nc6").fold(ChessEngine.initial()) { current, san ->
            val move = ChessEngine.legalMoves(current).first { ChessEngine.notation(current, it) == san }
            ChessEngine.apply(current, move)!!
        }
        val suggestions = OpeningIntelligence.suggest(state, listOf("e4", "e5", "Nf3", "Nc6"), games)
        assertEquals(1, suggestions.first { it.san == "Bc4" }.localGames)
    }
}
