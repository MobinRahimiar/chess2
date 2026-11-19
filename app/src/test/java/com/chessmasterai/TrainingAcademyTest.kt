package com.chessmasterai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingAcademyTest {
    @Test fun mateInOnePuzzleHasLegalVerifiedSolution() {
        val puzzle = TrainingAcademy.puzzles.first()
        val state = ChessEngine.fromFen(puzzle.fen)
        val move = ChessEngine.legalMoves(state).first { ChessEngine.square(it.from) + ChessEngine.square(it.to) == puzzle.solutionUci }
        val next = ChessEngine.apply(state, move)!!
        assertEquals(GameResult.CHECKMATE_WHITE, ChessEngine.result(next))
        assertTrue(TrainingAcademy.isSolved(puzzle, move))
    }

    @Test fun academyContainsThreeLearningAreas() {
        assertEquals(setOf(LessonLevel.BEGINNER, LessonLevel.INTERMEDIATE, LessonLevel.ENDGAME), TrainingAcademy.lessons.map { it.level }.toSet())
    }
}
