package com.chessmasterai

/** Convert Stockfish UCI coordinates (e2e4, e7e8q) into a legal move in this position. */
fun uciToMove(state: ChessState, uci: String): ChessMove? {
    if (uci.length !in 4..5) return null
    fun squareIndex(file: Char, rank: Char): Int {
        if (file !in 'a'..'h' || rank !in '1'..'8') return -1
        return (8 - rank.digitToInt()) * 8 + (file - 'a')
    }
    val from = squareIndex(uci[0], uci[1])
    val to = squareIndex(uci[2], uci[3])
    if (from < 0 || to < 0) return null
    val promotion = uci.getOrNull(4)?.let { if (state.whiteToMove) it.uppercaseChar() else it.lowercaseChar() }
    return ChessEngine.legalMoves(state).firstOrNull {
        it.from == from && it.to == to && (promotion == null || it.promotion == promotion)
    }
}

/** Plain-language explanation is deliberately derived from the engine's actual score/PV. */
fun explainEngineLine(line: EngineLine?, whiteToMove: Boolean = true): String {
    if (line == null) return "در حال جمع‌آوری خطوط تحلیل موتور…"
    val rawScore = line.score
    val numeric = rawScore.removePrefix("Mate ").toDoubleOrNull()
    val oriented = if (whiteToMove || numeric == null) rawScore else {
        val flipped = -numeric
        if (line.scoreIsMate) "Mate ${flipped.toInt()}" else String.format(java.util.Locale.US, "%+.2f", flipped)
    }
    val evaluation = when {
        line.scoreIsMate -> if (oriented.contains("-")) "موتور مات‌شدن اجباری سفید را در این خط می‌بیند." else "موتور امکان مات اجباری سیاه را می‌بیند."
        oriented.startsWith("+") -> "ارزیابی موتور به نفع سفید است ($oriented)."
        oriented.startsWith("-") -> "ارزیابی موتور به نفع سیاه است ($oriented)."
        else -> "ارزیابی موتور نزدیک به تعادل است ($oriented)."
    }
    val continuation = line.pv.take(5).joinToString("، ")
    val lineText = if (continuation.isBlank()) "" else " ادامهٔ محاسبه‌شده: $continuation."
    return "$evaluation این خط تا عمق ${line.depth} محاسبه شده است.$lineText"
}
