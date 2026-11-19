package com.chessmasterai

/** A curated offline opening line. weights are repertoire priors, not live global win probabilities. */
data class OpeningLine(
    val name: String,
    val moves: List<String>,
    val weight: Int,
    val eco: String = ""
)

data class OpeningSuggestion(
    val san: String,
    val openingName: String,
    val eco: String,
    val repertoireWeight: Int,
    val localGames: Int,
    val move: ChessMove?
)

/**
 * Offline opening knowledge layer.
 * - Curated lines provide useful opening guidance without network access.
 * - Locally saved games add device-specific frequency, never mislabeled as global statistics.
 * - Every suggested SAN is matched against legal moves before it can be played/highlighted.
 */
object OpeningIntelligence {
    val lines: List<OpeningLine> = listOf(
        OpeningLine("King's Pawn Opening", listOf("e4"), 100, "B00"),
        OpeningLine("Queen's Pawn Opening", listOf("d4"), 92, "D00"),
        OpeningLine("Réti Opening", listOf("Nf3"), 58, "A04"),
        OpeningLine("English Opening", listOf("c4"), 55, "A10"),
        OpeningLine("Bird Opening", listOf("f4"), 48, "A02"),
        OpeningLine("Van't Kruijs Opening", listOf("e3"), 35, "A00"),
        OpeningLine("Nimzowitsch-Larsen Attack", listOf("b3"), 42, "A01"),
        OpeningLine("King's Fianchetto Opening", listOf("g3"), 38, "A00"),
        OpeningLine("Italian Game", listOf("e4", "e5", "Nf3", "Nc6", "Bc4"), 95, "C50"),
        OpeningLine("Giuoco Piano", listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5"), 91, "C50"),
        OpeningLine("Two Knights Defense", listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Nf6"), 86, "C55"),
        OpeningLine("Ruy Lopez", listOf("e4", "e5", "Nf3", "Nc6", "Bb5"), 90, "C60"),
        OpeningLine("Berlin Defense", listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "Nf6"), 85, "C65"),
        OpeningLine("Morphy Defense", listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6"), 87, "C78"),
        OpeningLine("Scotch Game", listOf("e4", "e5", "Nf3", "Nc6", "d4"), 72, "C45"),
        OpeningLine("Four Knights Game", listOf("e4", "e5", "Nf3", "Nc6", "Nc3", "Nf6"), 70, "C46"),
        OpeningLine("King's Gambit", listOf("e4", "e5", "f4"), 64, "C30"),
        OpeningLine("Vienna Game", listOf("e4", "e5", "Nc3"), 61, "C25"),
        OpeningLine("Petrov Defense", listOf("e4", "e5", "Nf3", "Nf6"), 68, "C42"),
        OpeningLine("Philidor Defense", listOf("e4", "e5", "Nf3", "d6"), 57, "C41"),
        OpeningLine("Sicilian Defense", listOf("e4", "c5"), 94, "B20"),
        OpeningLine("Sicilian Najdorf", listOf("e4", "c5", "Nf3", "d6", "d4", "cxd4", "Nxd4", "Nf6", "Nc3", "a6"), 96, "B90"),
        OpeningLine("Sicilian Dragon", listOf("e4", "c5", "Nf3", "d6", "d4", "cxd4", "Nxd4", "Nf6", "Nc3", "g6"), 84, "B70"),
        OpeningLine("French Defense", listOf("e4", "e6"), 78, "C00"),
        OpeningLine("French Advance", listOf("e4", "e6", "d4", "d5", "e5"), 73, "C02"),
        OpeningLine("Caro-Kann Defense", listOf("e4", "c6"), 82, "B10"),
        OpeningLine("Caro-Kann Advance", listOf("e4", "c6", "d4", "d5", "e5"), 76, "B12"),
        OpeningLine("Pirc Defense", listOf("e4", "d6"), 60, "B07"),
        OpeningLine("Scandinavian Defense", listOf("e4", "d5"), 63, "B01"),
        OpeningLine("Alekhine Defense", listOf("e4", "Nf6"), 58, "B02"),
        OpeningLine("Modern Defense", listOf("e4", "g6"), 54, "B06"),
        OpeningLine("Queen's Gambit", listOf("d4", "d5", "c4"), 92, "D06"),
        OpeningLine("Queen's Gambit Accepted", listOf("d4", "d5", "c4", "dxc4"), 79, "D20"),
        OpeningLine("Slav Defense", listOf("d4", "d5", "c4", "c6"), 75, "D10"),
        OpeningLine("Semi-Slav Defense", listOf("d4", "d5", "c4", "c6", "Nf3", "Nf6", "Nc3", "e6"), 82, "D43"),
        OpeningLine("King's Indian Defense", listOf("d4", "Nf6", "c4", "g6"), 80, "E60"),
        OpeningLine("Nimzo-Indian Defense", listOf("d4", "Nf6", "c4", "e6", "Nc3", "Bb4"), 73, "E20"),
        OpeningLine("Queen's Indian Defense", listOf("d4", "Nf6", "c4", "e6", "Nf3", "b6"), 68, "E12"),
        OpeningLine("Grünfeld Defense", listOf("d4", "Nf6", "c4", "g6", "Nc3", "d5"), 77, "D80"),
        OpeningLine("Dutch Defense", listOf("d4", "f5"), 56, "A80"),
        OpeningLine("London System", listOf("d4", "d5", "Bf4"), 76, "D00"),
        OpeningLine("Catalan Opening", listOf("d4", "Nf6", "c4", "e6", "g3"), 68, "E00"),
        OpeningLine("Trompowsky Attack", listOf("d4", "Nf6", "Bg5"), 53, "A45"),
        OpeningLine("Colle System", listOf("d4", "d5", "Nf3", "Nf6", "e3"), 49, "D04"),
        OpeningLine("Stonewall Attack", listOf("d4", "d5", "e3", "Nf6", "Bd3"), 47, "D00"),
        OpeningLine("Old Indian Defense", listOf("d4", "Nf6", "c4", "d6"), 51, "A54"),
        OpeningLine("Benoni Defense", listOf("d4", "Nf6", "c4", "c5", "d5"), 62, "A60"),
        OpeningLine("Benko Gambit", listOf("d4", "Nf6", "c4", "c5", "d5", "b5"), 60, "A57"),
        OpeningLine("English Symmetrical", listOf("c4", "c5"), 53, "A30"),
        OpeningLine("English Four Knights", listOf("c4", "e5", "Nc3", "Nf6", "Nf3", "Nc6"), 57, "A28"),
        OpeningLine("Réti King's Indian Attack", listOf("Nf3", "d5", "g3"), 46, "A05"),
        OpeningLine("Bird's Defense", listOf("f4", "d5"), 42, "A03")
    )

    fun normalizeMove(token: String): String = token.trim()
        .replace(Regex("^[0-9]+\\.(\\.\\.)?"), "")
        .replace(Regex("[!?+#]+$"), "")
        .trim()

    fun movesFromNotation(notation: List<String>): List<String> = notation
        .flatMap { it.split(Regex("\\s+")) }
        .map(::normalizeMove)
        .filter { it.isNotBlank() && it !in setOf("...", "1-0", "0-1", "1/2-1/2", "*") }

    fun suggest(
        state: ChessState,
        playedNotation: List<String>,
        savedGames: List<SavedChessGame> = emptyList(),
        limit: Int = 5
    ): List<OpeningSuggestion> {
        val prefix = movesFromNotation(playedNotation)
        val repertoire = lines.mapNotNull { line ->
            if (prefix.size >= line.moves.size || line.moves.take(prefix.size) != prefix) return@mapNotNull null
            val next = line.moves[prefix.size]
            val legal = ChessEngine.legalMoves(state).firstOrNull { ChessEngine.notation(state, it) == next }
                ?: return@mapNotNull null
            Triple(line, next, legal)
        }
        // Count only games that share the exact played prefix; unrelated uses of a SAN move do not count.
        val localCounts = savedGames.mapNotNull { game ->
            val gameMoves = movesFromNotation(game.moves)
            if (gameMoves.take(prefix.size) == prefix && gameMoves.size > prefix.size) gameMoves[prefix.size] else null
        }.groupingBy { it }.eachCount()
        return repertoire.groupBy { it.second }.map { (san, matches) ->
            val best = matches.maxBy { it.first.weight }
            OpeningSuggestion(
                san = san,
                openingName = matches.maxBy { it.first.weight }.first.name,
                eco = matches.maxBy { it.first.weight }.first.eco,
                repertoireWeight = matches.maxOf { it.first.weight },
                localGames = localCounts[san] ?: 0,
                move = best.third
            )
        }.sortedWith(
            compareByDescending<OpeningSuggestion> { it.repertoireWeight }
                .thenByDescending { it.localGames }
        ).take(limit)
    }
}
