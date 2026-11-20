package dev.bluelink.protocol

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class GamesTest{
    @Test fun snakeWallCollisionEndsGame(){val g=SnakeGame(Random(1));repeat(20){g.tick()};assertTrue(g.over)}
    @Test fun snakeCannotReverse(){val g=SnakeGame();g.turn(-1,0);g.tick();assertEquals(Cell(9,8),g.body.first())}
    @Test fun snakeScoresOnFoodAndGrows(){val g=SnakeGame(Random(2));repeat(4){g.tick()};assertEquals(1,g.score);assertEquals(4,g.body.size);assertFalse(g.food in g.body)}
    @Test fun sudokuHasUniqueValidSolutionForEachDifficulty(){repeat(3){difficulty->val g=SudokuGame(difficulty,Random(10+difficulty));assertEquals(1,SudokuGame.solutions(g.cells.copyOf()));assertTrue(g.solution.all{it in 1..9});assertFalse(g.complete())}}
    @Test fun sudokuCluesCannotChangeAndSolvedGridCompletes(){val g=SudokuGame(0,Random(1));val clue=g.clues.indexOfFirst{it};val n=g.cells[clue];g.set(clue,0);assertEquals(n,g.cells[clue]);repeat(81){g.set(it,g.solution[it])};assertTrue(g.complete())}
    @Test fun sudokuHighlightsConflicts(){val g=SudokuGame(0,Random(7));val empty=g.clues.indexOfFirst{!it};g.set(empty,g.solution[(empty/9)*9+(empty%9+1)%9]);assertTrue(g.conflict(empty))}
    @Test fun runnerCollidesWithoutJumping(){val g=RunnerGame(Random(1));repeat(200){g.tick()};assertTrue(g.over)}
    @Test fun runnerJumpLandsAndCanClearObstacle(){val g=RunnerGame(Random(1));var frames=0;while(!g.over && g.score==0 && frames++<200){if(g.obstacle<.48f && g.height==0f)g.jump();g.tick()};assertFalse(g.over);assertTrue(g.score>0)}
    @Test fun blockRotationAndBoundsRemainValid(){val g=BlocksGame(Random(2));repeat(4){g.rotate()};assertTrue(g.valid(g.shape,g.x,g.y));repeat(20){g.move(-1)};assertTrue(g.valid(g.shape,g.x,g.y))}
    @Test fun blockLineClearingScores(){val g=BlocksGame(Random(1));for(i in 190..199)g.board[i]=1;g.drop();assertEquals(1,g.lines);assertEquals(100,g.score)}
    @Test fun filledSpawnTriggersGameOver(){val g=BlocksGame(Random(2));g.board.fill(1);g.tick();assertTrue(g.over || g.lines>0)}
}
