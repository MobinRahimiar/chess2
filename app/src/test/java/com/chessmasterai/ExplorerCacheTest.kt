package com.chessmasterai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExplorerCacheTest {
    private val sample = """
        {"white":600,"draws":300,"black":100,
         "moves":[{"uci":"g1f3","san":"Nf3","white":100,"draws":60,"black":40,"averageRating":2500},
                  {"uci":"e2e4","san":"e4","white":300,"draws":150,"black":50},
                  {"uci":"","san":"","white":1,"draws":0,"black":0}],
         "topGames":[{"id":"x"}],
         "opening":{"eco":"B00","name":"King's Pawn \"Game\""}}
    """.trimIndent()

    @Test fun parsesExplorerResponseAndSortsByGames() {
        val s = OpeningExplorerClient.parseResponse(sample)
        assertEquals(1000L, s.games)
        assertEquals(60, s.percent(s.whiteWins))
        assertEquals("B00", s.eco)
        assertEquals("King's Pawn \"Game\"", s.openingName)
        assertEquals(listOf("e4", "Nf3"), s.moves.map { it.san })   // blank SAN row skipped, sorted by games
    }

    @Test fun emptyExplorerResponseIsHandled() {
        val s = OpeningExplorerClient.parseResponse("""{"white":0,"draws":0,"black":0,"moves":[]}""")
        assertEquals(0L, s.games); assertEquals(0, s.percent(s.whiteWins)); assertNull(s.eco)
    }

    @Test(expected = MiniJson.JsonException::class)
    fun malformedJsonIsRejected() { OpeningExplorerClient.parseResponse("{\"white\": 1,") }

    @Test fun urlUsesPositionFenAndIsEncoded() {
        val url = OpeningExplorerClient.buildUrl(ChessEngine.initial())
        assertTrue(url.startsWith("https://explorer.lichess.org/masters?fen="))
        assertFalse(url.contains(" "))
    }

    @Test fun cacheRoundTripsThroughStorageAndSurvivesRestart() {
        val store = MemoryExplorerStore()
        val state = ChessEngine.initial()
        ExplorerCache(store).put(state, OpeningExplorerClient.parseResponse(sample), 12345L)
        val reopened = ExplorerCache(store).get(state)
        assertNotNull(reopened)
        assertEquals(12345L, reopened!!.savedAtMillis)
        assertEquals("King's Pawn \"Game\"", reopened.stats.openingName)
        assertEquals(listOf("e4", "Nf3"), reopened.stats.moves.map { it.san })
        assertEquals(1000L, reopened.stats.games)
    }

    @Test fun keyIgnoresMoveCountersButNotPosition() {
        val c = ExplorerCache(MemoryExplorerStore())
        val a = ChessEngine.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val b = ChessEngine.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 7 9")
        val other = ChessEngine.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
        assertEquals(c.key(a), c.key(b))
        assertFalse(c.key(a) == c.key(other))
    }

    @Test fun corruptCacheIsIgnoredNotFatal() {
        val c = ExplorerCache(MemoryExplorerStore("{{{ not json"))
        assertEquals(0, c.size)
    }

    @Test fun cacheIsBoundedAndDropsOldestEntries() {
        val c = ExplorerCache(MemoryExplorerStore(), maxEntries = 2)
        val stats = OpeningExplorerClient.parseResponse(sample)
        val s1 = ChessEngine.initial()
        val s2 = ChessEngine.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
        val s3 = ChessEngine.fromFen("rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq d3 0 1")
        c.put(s1, stats, 1); c.put(s2, stats, 2); c.put(s3, stats, 3)
        assertEquals(2, c.size); assertNull(c.get(s1)); assertNotNull(c.get(s3))
    }

    @Test fun onlineSuccessIsSavedAndLaterUsableOfflineWithoutToken() {
        val cache = ExplorerCache(MemoryExplorerStore())
        val state = ChessEngine.initial()
        val online = ExplorerCache.lookup(cache, state, "tok", 100L) { _, _ -> OpeningExplorerClient.parseResponse(sample) }
        assertFalse(online.fromCache)
        val offline = ExplorerCache.lookup(cache, state, "", 200L) { _, _ -> fail("must not go online"); throw IllegalStateException() }
        assertTrue(offline.fromCache); assertEquals(100L, offline.savedAtMillis)
        assertEquals(1000L, offline.stats.games)
    }

    @Test fun networkFailureFallsBackToCacheWithWarning() {
        val cache = ExplorerCache(MemoryExplorerStore())
        val state = ChessEngine.initial()
        cache.put(state, OpeningExplorerClient.parseResponse(sample), 50L)
        val r = ExplorerCache.lookup(cache, state, "tok", 100L) { _, _ -> throw IllegalStateException("Lichess rate limit reached.") }
        assertTrue(r.fromCache)
        assertTrue(r.warning!!.contains("rate limit"))
    }

    @Test fun failureWithoutCacheAndMissingTokenWithoutCacheAreErrors() {
        val cache = ExplorerCache(MemoryExplorerStore())
        val state = ChessEngine.initial()
        try { ExplorerCache.lookup(cache, state, "tok", 1L) { _, _ -> throw IllegalStateException("boom") }; fail() }
        catch (e: IllegalStateException) { assertEquals("boom", e.message) }
        try { ExplorerCache.lookup(cache, state, "", 1L) { _, _ -> fail(); throw IllegalStateException() }; fail() }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("token")) }
    }

    @Test fun miniJsonHandlesEscapesNestingAndRejectsGarbage() {
        val v = MiniJson.parse("""{"a":[1,2.5,true,null,"x\né"],"b":{"c":-3}}""").asObj()!!
        assertEquals(listOf<Any?>(1L, 2.5, true, null, "x\né"), v["a"])
        assertEquals(-3L, v["b"].asObj()!!["c"])
        for (bad in listOf("", "{", "[1,]", "{\"a\" 1}", "tru", "\"abc", "1 2")) {
            try { MiniJson.parse(bad); fail("should reject: $bad") } catch (_: MiniJson.JsonException) { }
        }
    }
}
