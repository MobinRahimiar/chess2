package com.chessmasterai

/** Parsed PGN record. SAN is retained as source notation; it is not treated as engine-verified until replayed. */
data class PgnGame(
    val event: String = "Imported game",
    val white: String = "White",
    val black: String = "Black",
    val date: String = "",
    val result: String = "*",
    val moves: List<String>
)

/** Conservative PGN reader for standard SAN movetext. Removes comments, NAGs and nested variations. */
object PgnDatabase {
    private val tagRegex = Regex("^\\s*\\[([A-Za-z0-9_]+)\\s+\"(.*)\"\\]\\s*$")
    private val moveNumber = Regex("^\\d+\\.(\\.\\.)?$")
    private val results = setOf("1-0", "0-1", "1/2-1/2", "*")

    fun parse(text: String, maxGames: Int = 20_000): List<PgnGame> {
        if (text.isBlank() || maxGames <= 0) return emptyList()
        val games = mutableListOf<PgnGame>()
        var tags = linkedMapOf<String, String>()
        val body = StringBuilder()
        fun flush() {
            val tokens = tokenize(body.toString())
            val moves = tokens.filterNot { it in results || moveNumber.matches(it) }
                .map { it.trim().replace(Regex("[!?]+$"), "") }
                .filter { it.isNotBlank() && it != "..." }
            if (moves.isNotEmpty() && games.size < maxGames) {
                games += PgnGame(
                    event = tags["Event"] ?: "Imported game",
                    white = tags["White"] ?: "White",
                    black = tags["Black"] ?: "Black",
                    date = tags["Date"] ?: "",
                    result = tags["Result"] ?: tokens.lastOrNull { it in results } ?: "*",
                    moves = moves
                )
            }
            tags = linkedMapOf()
            body.setLength(0)
        }
        text.lineSequence().forEach { line ->
            val match = tagRegex.matchEntire(line)
            if (match != null) {
                if (body.isNotEmpty()) flush()
                tags[match.groupValues[1]] = match.groupValues[2]
            } else {
                body.append(line).append('\n')
            }
        }
        flush()
        return games
    }

    fun tokenize(raw: String): List<String> {
        val out = StringBuilder()
        var braceDepth = 0
        var variationDepth = 0
        var semicolonComment = false
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                semicolonComment -> if (c == '\n') { semicolonComment = false; out.append(' ') }
                braceDepth > 0 -> if (c == '}') braceDepth-- else if (c == '{') braceDepth++
                variationDepth > 0 -> when (c) { '(' -> variationDepth++; ')' -> variationDepth-- }
                c == ';' -> semicolonComment = true
                c == '{' -> braceDepth = 1
                c == '(' -> variationDepth = 1
                c == '\u0000' -> Unit
                else -> out.append(c)
            }
            i++
        }
        return out.toString().split(Regex("\\s+")).map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("$") }
            .map { it.replace(Regex("^\\d+\\.(\\.\\.)?"), "") }
            .filter { it.isNotBlank() }
    }

    /**
     * Replays a parsed mainline through the local rules engine. Returns canonical SAN-like
     * notation only when every move is legal; malformed or non-standard records are excluded
     * from opening-frequency statistics instead of polluting the local database.
     */
    fun validateMainline(game: PgnGame): List<String>? {
        var state = ChessEngine.initial()
        val canonical = mutableListOf<String>()
        for (raw in game.moves) {
            val san = raw.trim().replace('0', 'O').replace(Regex("[!?+#]+$"), "")
            if (san.isBlank() || san == "e.p.") continue
            val move = resolveSan(state, san) ?: return null
            canonical += ChessEngine.notation(state, move)
            state = ChessEngine.apply(state, move) ?: return null
        }
        return canonical.takeIf { it.isNotEmpty() }
    }

    private fun resolveSan(state: ChessState, san: String): ChessMove? {
        if (san == "O-O" || san == "O-O-O") {
            val target = if (san == "O-O") 6 else 2
            return ChessEngine.legalMoves(state).singleOrNull { it.castle && it.to % 8 == target }
        }
        val destination = Regex("([a-h][1-8])(?:=[QRBN])?$").find(san) ?: return null
        val square = destination.groupValues[1]
        val target = (8 - square[1].digitToInt()) * 8 + (square[0] - 'a')
        val promotion = Regex("=([QRBN])$").find(san)?.groupValues?.get(1)?.firstOrNull()
        val prefix = san.substring(0, destination.range.first)
        val capture = prefix.contains('x')
        val compact = prefix.replace("x", "")
        val pieceLetter = compact.firstOrNull()?.takeIf { it in "KQRBN" }
        val pieceType = pieceLetter?.lowercaseChar() ?: 'p'
        val disambiguation = if (pieceLetter != null) compact.drop(1) else compact
        if (disambiguation.length > 2 || disambiguation.any { it !in "abcdefgh12345678" }) return null

        val candidates = ChessEngine.legalMoves(state).filter { move ->
            val moving = state.board[move.from].lowercaseChar()
            val isCapture = state.board[move.to] != '.' || move.enPassant
            if (moving != pieceType || move.to != target || isCapture != capture) return@filter false
            if ((move.promotion?.uppercaseChar()) != promotion) return@filter false
            disambiguation.all { hint ->
                when (hint) {
                    in 'a'..'h' -> ChessEngine.square(move.from)[0] == hint
                    in '1'..'8' -> ChessEngine.square(move.from)[1] == hint
                    else -> false
                }
            }
        }
        return candidates.singleOrNull()
    }
}
