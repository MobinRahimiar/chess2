package com.chessmasterai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PgnDatabaseTest {
    @Test fun parsesHeadersAndMainlineOnly() {
        val pgn = """
            [Event "Test Open"]
            [White "Alice"]
            [Black "Bob"]
            [Result "1-0"]

            1. e4 e5 2. Nf3 Nc6 {comment} 3. Bb5 (3. Bc4 Nf6) a6 1-0
        """.trimIndent()
        val games = PgnDatabase.parse(pgn)
        assertEquals(1, games.size)
        assertEquals("Test Open", games[0].event)
        assertEquals("Alice", games[0].white)
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6"), games[0].moves)
        assertEquals("1-0", games[0].result)
    }

    @Test fun parsesMultipleGamesAndSkipsEmptyRecords() {
        val pgn = """
            [Event "One"]

            1. d4 d5 1/2-1/2

            [Event "Two"]

            1. c4 e5 *
        """.trimIndent()
        val games = PgnDatabase.parse(pgn)
        assertEquals(2, games.size)
        assertEquals(listOf("d4", "d5"), games[0].moves)
        assertEquals(listOf("c4", "e5"), games[1].moves)
    }

    @Test fun ignoresNagAndComments() {
        val moves = PgnDatabase.tokenize("1. e4 $1 e5 ; ignore this\n2. Nf3 {text} Nc6")
        assertTrue(moves.contains("e4"))
        assertTrue(moves.contains("Nf3"))
        assertTrue(!moves.contains("ignore"))
    }

    @Test fun validatesMainlineAndCanonicalizesCheckAnnotations() {
        val game = PgnDatabase.parse("[Event \"Valid\"]\n\n1. e4 e5 2. Nf3 Nc6 3. Bb5+ a6 *").single()
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6"), PgnDatabase.validateMainline(game))
    }

    @Test fun rejectsIllegalPgnMainlineFromLocalStatistics() {
        val game = PgnDatabase.parse("[Event \"Invalid\"]\n\n1. e4 e5 2. Qh5 Kf7 *").single()
        assertEquals(null, PgnDatabase.validateMainline(game))
    }
}
