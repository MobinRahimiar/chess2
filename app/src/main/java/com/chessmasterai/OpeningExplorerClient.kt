package com.chessmasterai

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ExplorerMoveStats(
    val san: String,
    val uci: String,
    val whiteWins: Long,
    val draws: Long,
    val blackWins: Long
) {
    val games: Long get() = whiteWins + draws + blackWins
}

data class OpeningExplorerStats(
    val openingName: String?,
    val eco: String?,
    val whiteWins: Long,
    val draws: Long,
    val blackWins: Long,
    val moves: List<ExplorerMoveStats>,
    val source: String = "Lichess Masters database"
) {
    val games: Long get() = whiteWins + draws + blackWins
    fun percent(value: Long): Int = if (games <= 0L) 0 else ((value * 100.0) / games).toInt()
}

/**
 * Optional online opening statistics (Lichess Masters database). The app stays fully usable offline.
 * The token is supplied by the user for the request only and is never persisted.
 * The query uses the position FEN, so transpositions and games restored from a FEN are handled.
 */
object OpeningExplorerClient {
    private const val ENDPOINT = "https://explorer.lichess.org/masters"

    fun historyToUci(history: List<ChessState>): List<String> {
        if (history.size < 2) return emptyList()
        return history.zipWithNext().mapNotNull { (before, after) ->
            val afterFen = ChessEngine.fen(after)
            val move = ChessEngine.legalMoves(before).firstOrNull { candidate ->
                ChessEngine.fen(ChessEngine.applyLegal(before, candidate)) == afterFen
            } ?: return@mapNotNull null
            ChessEngine.square(move.from) + ChessEngine.square(move.to) +
                (move.promotion?.lowercaseChar()?.toString() ?: "")
        }
    }

    fun buildUrl(state: ChessState): String =
        ENDPOINT + "?fen=" + URLEncoder.encode(ChessEngine.fen(state), "UTF-8")

    /** Parses the explorer JSON. Pure function, unit-tested. Throws on malformed bodies. */
    fun parseResponse(body: String): OpeningExplorerStats {
        val json = MiniJson.parse(body).asObj() ?: throw IllegalStateException("Unexpected explorer response format.")
        val opening = json["opening"].asObj()
        val moves = json["moves"].asList().orEmpty().mapNotNull { row ->
            val r = row.asObj() ?: return@mapNotNull null
            val san = r["san"].asText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ExplorerMoveStats(san, r["uci"].asText(), r["white"].asLong(), r["draws"].asLong(), r["black"].asLong())
        }.sortedByDescending { it.games }
        return OpeningExplorerStats(
            openingName = opening?.get("name").asText().takeIf { it.isNotBlank() },
            eco = opening?.get("eco").asText().takeIf { it.isNotBlank() },
            whiteWins = json["white"].asLong(), draws = json["draws"].asLong(), blackWins = json["black"].asLong(),
            moves = moves
        )
    }

    fun fetchMasters(state: ChessState, token: String): OpeningExplorerStats {
        require(token.isNotBlank()) { "A Lichess API token is required for online explorer access." }
        val connection = (URL(buildUrl(state)).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 18_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${token.trim()}")
            setRequestProperty("User-Agent", "ChessMasterAI/0.3")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 401 || code == 403) throw IllegalStateException("Lichess token was rejected (HTTP $code). Check the token and its read access.")
            if (code == 429) throw IllegalStateException("Lichess rate limit reached. Wait before trying again.")
            if (code !in 200..299) throw IllegalStateException("Lichess request failed (HTTP $code).")
            return try { parseResponse(body) } catch (e: MiniJson.JsonException) {
                throw IllegalStateException("Lichess returned a response that could not be read.")
            }
        } finally {
            connection.disconnect()
        }
    }
}
