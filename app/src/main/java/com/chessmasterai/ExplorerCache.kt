package com.chessmasterai

/** Persistent text storage for the cache (SharedPreferences on Android, memory in tests). */
interface ExplorerCacheStore {
    fun read(): String?
    fun write(text: String)
}

class MemoryExplorerStore(var text: String? = null) : ExplorerCacheStore {
    override fun read() = text
    override fun write(text: String) { this.text = text }
}

data class CachedExplorerStats(val stats: OpeningExplorerStats, val savedAtMillis: Long)

/** Outcome of a lookup: where the numbers came from and any caveat the UI must show. */
data class ExplorerLookup(val stats: OpeningExplorerStats, val fromCache: Boolean, val savedAtMillis: Long, val warning: String?)

/**
 * Offline store of previously fetched Lichess Masters statistics, keyed by position
 * (board + side + castling + en-passant; move counters are ignored so transpositions share an entry).
 * Bounded (LRU by save time). Contains only public aggregate statistics, never the token.
 */
class ExplorerCache(private val store: ExplorerCacheStore, private val maxEntries: Int = 200) {
    private val entries = LinkedHashMap<String, CachedExplorerStats>()
    init { load() }

    fun key(state: ChessState): String = ChessEngine.fen(state).split(" ").take(4).joinToString(" ")

    fun get(state: ChessState): CachedExplorerStats? = entries[key(state)]

    fun put(state: ChessState, stats: OpeningExplorerStats, nowMillis: Long) {
        val k = key(state)
        entries.remove(k)
        entries[k] = CachedExplorerStats(stats, nowMillis)
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
        store.write(encode())
    }

    val size: Int get() = entries.size

    private fun encode(): String = entries.entries.joinToString(",", "[", "]") { (k, v) ->
        val s = v.stats
        "{\"k\":${MiniJson.quote(k)},\"t\":${v.savedAtMillis},\"o\":${MiniJson.quote(s.openingName.orEmpty())}," +
            "\"e\":${MiniJson.quote(s.eco.orEmpty())},\"w\":${s.whiteWins},\"d\":${s.draws},\"b\":${s.blackWins},\"m\":" +
            s.moves.joinToString(",", "[", "]") { m ->
                "{\"san\":${MiniJson.quote(m.san)},\"uci\":${MiniJson.quote(m.uci)},\"w\":${m.whiteWins},\"d\":${m.draws},\"b\":${m.blackWins}}"
            } + "}"
    }

    private fun load() {
        val raw = runCatching { store.read() }.getOrNull() ?: return
        // A corrupt cache must never crash the app: it is simply ignored.
        runCatching {
            MiniJson.parse(raw).asList().orEmpty().forEach { item ->
                val o = item.asObj() ?: return@forEach
                val k = o["k"].asText().takeIf { it.isNotBlank() } ?: return@forEach
                val moves = o["m"].asList().orEmpty().mapNotNull { row ->
                    val r = row.asObj() ?: return@mapNotNull null
                    val san = r["san"].asText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    ExplorerMoveStats(san, r["uci"].asText(), r["w"].asLong(), r["d"].asLong(), r["b"].asLong())
                }
                entries[k] = CachedExplorerStats(
                    OpeningExplorerStats(
                        o["o"].asText().takeIf { it.isNotBlank() }, o["e"].asText().takeIf { it.isNotBlank() },
                        o["w"].asLong(), o["d"].asLong(), o["b"].asLong(), moves, "Lichess Masters database (offline copy)"
                    ),
                    o["t"].asLong()
                )
            }
        }.onFailure { entries.clear() }
    }

    companion object {
        /**
         * Online-first lookup with offline fallback.
         *  - no token        -> cached copy only (works fully offline)
         *  - fetch succeeds  -> stored and returned
         *  - fetch fails     -> cached copy with an explicit warning, otherwise the error propagates
         */
        fun lookup(
            cache: ExplorerCache,
            state: ChessState,
            token: String,
            nowMillis: Long,
            fetcher: (ChessState, String) -> OpeningExplorerStats
        ): ExplorerLookup {
            val cached = cache.get(state)
            if (token.isBlank()) {
                if (cached == null) throw IllegalStateException("No saved statistics for this position. Enter a Lichess token to download them once.")
                return ExplorerLookup(cached.stats, true, cached.savedAtMillis, "Offline copy; enter a token to refresh.")
            }
            return try {
                val fresh = fetcher(state, token)
                cache.put(state, fresh, nowMillis)
                ExplorerLookup(fresh, false, nowMillis, null)
            } catch (e: Exception) {
                if (cached == null) throw e
                ExplorerLookup(cached.stats, true, cached.savedAtMillis, "Online lookup failed (${e.message}); showing the saved offline copy.")
            }
        }
    }
}
