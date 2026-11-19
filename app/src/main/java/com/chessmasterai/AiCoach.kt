package com.chessmasterai

/** Persian coaching insights built only from Stockfish MultiPV output and legal move replay. */
object AiCoach {
    fun insights(state: ChessState, report: EngineReport): List<String> {
        val result = mutableListOf<String>()
        val lines = report.lines.sortedBy { it.rank }
        val best = lines.firstOrNull() ?: return listOf("هنوز خط معتبری از موتور دریافت نشده است.")
        val bestMove = uciToMove(state, best.bestMove)
        val bestSan = bestMove?.let { ChessEngine.notation(state, it) } ?: best.bestMove
        result += "پیشنهاد اصلی موتور: $bestSan. این انتخاب در عمق ${best.depth} بررسی شده و امتیاز ثبت‌شدهٔ آن ${best.score} است."

        if (lines.size >= 2) {
            val second = lines[1]
            val a = scoreToPawns(best.score)
            val b = scoreToPawns(second.score)
            if (a != null && b != null) {
                val gap = kotlin.math.abs(a - b)
                val gapText = String.format(java.util.Locale.US, "%.2f", gap)
                result += when {
                    gap >= 1.5 -> "اختلاف دو گزینهٔ برتر حدود $gapText پیاده است؛ موتور گزینهٔ اول را به‌وضوح ترجیح می‌دهد."
                    gap >= 0.6 -> "اختلاف دو گزینهٔ اول $gapText پیاده است؛ گزینهٔ اول بهتر ارزیابی شده، اما گزینهٔ دوم هم قابل بررسی است."
                    else -> "دو گزینهٔ اول فقط $gapText پیاده اختلاف دارند؛ چند انتخاب نزدیک به هم وجود دارد."
                }
            } else if (best.scoreIsMate || second.scoreIsMate) {
                result += "حداقل یکی از گزینه‌های برتر شامل ارزیابی مات است؛ عدد مات را همراه با ادامهٔ خط بررسی کن."
            }
        } else {
            result += "فقط یک خط اصلی موجود است؛ برای مقایسهٔ گزینه‌ها تحلیل MultiPV با چند خط لازم است."
        }

        val replay = replayPv(state, best.pv, 8)
        if (replay.isNotEmpty()) result += "ادامهٔ پیشنهادی به زبان نمادین: ${replay.joinToString(" ")}. این دنباله از خط اصلی موتور بازپخش شده است."

        if (bestMove != null) {
            val next = ChessEngine.apply(state, bestMove)
            if (next != null) {
                val captured = state.board[bestMove.to] != '.' || bestMove.enPassant
                val check = ChessEngine.inCheck(next)
                val resultAfter = ChessEngine.result(next)
                val details = mutableListOf<String>()
                if (captured) details += "گرفتن مهره"
                if (check) details += "کیش"
                if (resultAfter == GameResult.CHECKMATE_WHITE || resultAfter == GameResult.CHECKMATE_BLACK) details += "مات"
                if (details.isNotEmpty()) result += "نکتهٔ قابل مشاهده پس از حرکت پیشنهادی: ${details.joinToString(" و ")}."
                else result += "حرکت پیشنهادی الزاماً گرفتن مهره یا کیش فوری نیست؛ ممکن است ارزش آن در بهبود موقعیت و ادامهٔ محاسبه‌شده باشد."
            }
        }
        return result
    }

    fun replayPv(state: ChessState, pv: List<String>, maxMoves: Int = 8): List<String> {
        var position = state
        val san = mutableListOf<String>()
        for (uci in pv.take(maxMoves)) {
            val move = uciToMove(position, uci) ?: break
            san += ChessEngine.notation(position, move)
            position = ChessEngine.apply(position, move) ?: break
        }
        return san
    }

    private fun scoreToPawns(score: String): Double? {
        if (score.startsWith("Mate")) return null
        return score.toDoubleOrNull()
    }
}
