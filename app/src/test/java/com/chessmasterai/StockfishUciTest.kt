package com.chessmasterai

import org.junit.Assert.*
import org.junit.Test

class StockfishUciTest {
    @Test fun parsesCentipawnMultiPvLine() {
        val parsed = parseUciInfoLine("info depth 15 seldepth 22 multipv 2 score cp 38 nodes 12000 nps 800000 pv e2e4 e7e5 g1f3")!!
        assertEquals(2, parsed.rank)
        assertEquals(15, parsed.depth)
        assertEquals("+0.38", parsed.score)
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), parsed.pv)
        assertEquals(12000L, parsed.nodes)
        assertEquals(800000L, parsed.nps)
    }

    @Test fun parsesMateScore() {
        val parsed = parseUciInfoLine("info depth 20 multipv 1 score mate -3 nodes 99 pv h2h4")!!
        assertTrue(parsed.scoreIsMate)
        assertEquals("Mate -3", parsed.score)
    }

    @Test fun ignoresIncompleteAndUnsupportedScoreLines() {
        assertNull(parseUciInfoLine("info depth 8 nodes 20"))
        assertNull(parseUciInfoLine("info depth 8 score lowerbound 22 pv e2e4"))
        assertNull(parseUciInfoLine("bestmove e2e4"))
    }

    @Test fun clampsEngineSettingsToSafeRanges() {
        val settings = StockfishSettings(threads = 99, hashMb = 4096, multiPv = 99, depth = 99)
            .normalized(4)
        assertEquals(4, settings.threads)
        assertEquals(512, settings.hashMb)
        assertEquals(5, settings.multiPv)
        assertEquals(30, settings.depth)
    }

    @Test fun convertsOnlyLegalUciMoves() {
        val initial = ChessEngine.initial()
        assertNotNull(uciToMove(initial, "e2e4"))
        assertNull(uciToMove(initial, "e2e5"))
        assertNull(uciToMove(initial, "z9z1"))
    }

    @Test fun validatesPromotionMoveAgainstLegalPromotionChoice() {
        val promotionPosition = ChessEngine.fromFen("7k/P7/8/8/8/8/8/7K w - - 0 1")
        assertNotNull(uciToMove(promotionPosition, "a7a8q"))
        assertNull(uciToMove(promotionPosition, "a7a8x"))
    }

    @Test fun settingsNeverUseZeroThreadsOrHash() {
        val settings = StockfishSettings(threads = 0, hashMb = 0, multiPv = 0, depth = 0)
            .normalized(1)
        assertEquals(1, settings.threads)
        assertEquals(16, settings.hashMb)
        assertEquals(1, settings.multiPv)
        assertEquals(1, settings.depth)
    }
}
