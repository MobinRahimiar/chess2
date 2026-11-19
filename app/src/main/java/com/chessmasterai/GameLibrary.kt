package com.chessmasterai

import android.content.Context
import android.util.Base64
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SavedChessGame(
    val id: String,
    val title: String,
    val createdAt: Long,
    val playerColor: String,
    val fens: List<String>,
    val moves: List<String>
)

/** Small offline game library. Games are stored locally; no account or network is required. */
class GameLibrary(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chess_master_games", Context.MODE_PRIVATE)

    fun all(): List<SavedChessGame> = prefs.getStringSet("games", emptySet()).orEmpty().mapNotNull(::decode)
        .sortedByDescending { it.createdAt }

    fun save(title: String, playerColor: String, states: List<ChessState>, moves: List<String>): SavedChessGame {
        require(states.isNotEmpty())
        val game = SavedChessGame(UUID.randomUUID().toString(), title.ifBlank { "Chess game" }, System.currentTimeMillis(), playerColor,
            states.map(ChessEngine::fen), moves.toList())
        val updated = (all() + game).map(::encode).toSet()
        prefs.edit().putStringSet("games", updated).apply()
        return game
    }

    /** Imports standard PGN games into the local frequency database. Imported records are statistics-only. */
    fun importPgn(text: String): Int {
        val imported = PgnDatabase.parse(text, maxGames = 1000)
            .mapNotNull { game -> PgnDatabase.validateMainline(game)?.let { game to it } }
        if (imported.isEmpty()) return 0
        val existing = all()
        val additions = imported.map { (pgn, validatedMoves) ->
            SavedChessGame(
                id = UUID.randomUUID().toString(),
                title = "${pgn.event}: ${pgn.white} - ${pgn.black}".take(120),
                createdAt = System.currentTimeMillis(),
                playerColor = "Imported PGN",
                fens = emptyList(),
                moves = validatedMoves
            )
        }
        prefs.edit().putStringSet("games", (existing + additions).map(::encode).toSet()).apply()
        return additions.size
    }

    fun delete(id: String) {
        prefs.edit().putStringSet("games", all().filterNot { it.id == id }.map(::encode).toSet()).apply()
    }

    fun toPgn(game: SavedChessGame): String {
        val date = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(game.createdAt))
        val result = game.fens.lastOrNull()?.let { runCatching { ChessEngine.fromFen(it) }.getOrNull() }
        val repetitions = result?.let { final -> game.fens.count { fen -> runCatching { ChessEngine.positionKey(ChessEngine.fromFen(fen)) == ChessEngine.positionKey(final) }.getOrDefault(false) } } ?: 1
        val outcome = when (result?.let { ChessEngine.result(it, repetitions) }) {
            GameResult.CHECKMATE_WHITE -> "1-0"
            GameResult.CHECKMATE_BLACK -> "0-1"
            GameResult.STALEMATE, GameResult.DRAW_FIFTY_MOVE, GameResult.DRAW_REPETITION, GameResult.DRAW_INSUFFICIENT -> "1/2-1/2"
            else -> "*"
        }
        val sanLike = game.moves.mapIndexed { index, move ->
            val notation = move.replace(Regex("^\\d+\\.{1,3}\\s*"), "")
            if (index % 2 == 0) "${index / 2 + 1}. $notation" else notation
        }.joinToString(" ")
        return "[Event \"ChessMaster AI game\"]\n[Site \"Local device\"]\n[Date \"$date\"]\n[White \"${if (game.playerColor == "White") "Player" else "Stockfish/AI"}\"]\n[Black \"${if (game.playerColor == "Black") "Player" else "Stockfish/AI"}\"]\n[Result \"$outcome\"]\n\n${if (sanLike.isBlank()) "" else "$sanLike "}$outcome\n"
    }

    private fun encode(g: SavedChessGame): String = listOf(g.id, g.title, g.createdAt.toString(), g.playerColor,
        g.fens.joinToString("\u001e"), g.moves.joinToString("\u001e")).joinToString("\u001f")
        .toByteArray(Charsets.UTF_8).let { Base64.encodeToString(it, Base64.NO_WRAP) }

    private fun decode(raw: String): SavedChessGame? = runCatching {
        val parts = String(Base64.decode(raw, Base64.NO_WRAP), Charsets.UTF_8).split('\u001f')
        require(parts.size == 6)
        SavedChessGame(parts[0], parts[1], parts[2].toLong(), parts[3], parts[4].split('\u001e').filter(String::isNotBlank), parts[5].split('\u001e').filter(String::isNotBlank))
    }.getOrNull()
}
