package com.chessmasterai

enum class LessonLevel { BEGINNER, INTERMEDIATE, ENDGAME }

data class ChessLesson(val title: String, val level: LessonLevel, val summary: String, val keyPoints: List<String>)

data class ChessPuzzle(val id: String, val title: String, val fen: String, val solutionUci: String, val explanation: String, val theme: String)

object TrainingAcademy {
    val lessons = listOf(
        ChessLesson("اصول گشایش", LessonLevel.BEGINNER, "در شروع بازی برای توسعه و امنیت شاه رقابت کن.", listOf("اسب‌ها و فیل‌ها را سریع توسعه بده.", "برای کنترل مرکز تلاش کن.", "بی‌دلیل با یک مهره چند بار حرکت نکن.", "در زمان مناسب قلعه برو و شاه را امن کن.")),
        ChessLesson("ساختن نقشهٔ میانی", LessonLevel.INTERMEDIATE, "قبل از حرکت، تهدید حریف و بدترین مهرهٔ خودت را پیدا کن.", listOf("اول کیش‌ها، گرفتن‌ها و تهدیدها را بررسی کن.", "ضعف خانه‌ها و ستون‌های باز را ارزیابی کن.", "اگر حملهٔ مستقیم نیست، بدترین مهره را بهتر کن.")),
        ChessLesson("اصول آخر بازی", LessonLevel.ENDGAME, "در آخر بازی فعالیت شاه و پیادهٔ آزاد اهمیت زیادی دارد.", listOf("شاه را به مرکز و میدان نبرد نزدیک کن.", "پیادهٔ آزاد را با شاه پشتیبانی کن.", "قبل از پیش‌رَوی پیاده، قانون مربع را بررسی کن."))
    )

    // Verified against the bundled legal-move core: Qf8# is checkmate.
    val puzzles = listOf(
        ChessPuzzle("mate-in-one-001", "مات در یک حرکت", "7k/5Q2/6K1/8/8/8/8/8 w - - 0 1", "f7f8", "وزیر را به f8 ببر. شاه سیاه در خانهٔ h8 کیش است و تمام خانه‌های فرار پوشش داده شده‌اند.", "مات با وزیر و شاه")
    )

    fun isSolved(puzzle: ChessPuzzle, move: ChessMove): Boolean =
        ChessEngine.square(move.from) + ChessEngine.square(move.to) == puzzle.solutionUci
}
