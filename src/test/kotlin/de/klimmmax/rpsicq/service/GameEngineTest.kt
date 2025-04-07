package de.klimmmax.rpsicq.service

import de.klimmmax.rpsicq.model.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.util.*

class GameEngineTest {

    private lateinit var gameEngine: GameEngine
    private lateinit var p1: Player
    private lateinit var p2: Player

    @BeforeEach
    fun setUp() {
        p1 = Player(UUID.randomUUID(), "Player 1", PLAYER_STATE.IN_GAME)
        p2 = Player(UUID.randomUUID(), "Player 2", PLAYER_STATE.IN_GAME)
        gameEngine = GameEngine()
    }

    @Test
    fun `initial game setup places figures correctly`() {
        val game = gameEngine.createInitialGame(p1, p2)

        val board = game.board
        val boardSize = board.size

        // Verify player 1 figures in rows 0 and 1
        for (y in 0..1) {
            for (x in 0 until boardSize) {
                val figure = board[x][y].figure
                assertNotNull(figure, "Expected figure at ($x, $y) for Player 1")
                assertEquals(p1.id, figure?.ownerId, "Figure at ($x, $y) should belong to Player 1")
            }
        }

        // Verify player 2 figures in rows 5 and 6
        for (y in 4..5) {
            for (x in 0 until boardSize) {
                val figure = board[x][y].figure
                assertNotNull(figure, "Expected figure at ($x, $y) for Player 2")
                assertEquals(p2.id, figure?.ownerId, "Figure at ($x, $y) should belong to Player 2")
            }
        }

        // Verify all middle rows are empty
        for (y in 2..3) {
            for (x in 0 until boardSize) {
                assertNull(board[x][y].figure, "Expected no figure at ($x, $y)")
            }
        }

        assertEquals(GAME_PHASE.SETUP, game.currentPhase, "After initialising, we must be in the SETUP phase")
        val totalFigures = board.flatten().count { it.figure != null }
        assertEquals(28, totalFigures, "Each player should have 14 figures (2 rows of 7)")
        val totalNumberOfKingsAndTraps = board.flatten().mapNotNull { it.figure }.count { it.isTrap || it.isKing }
        assertEquals(0, totalNumberOfKingsAndTraps, "There must be no king or trap placed just yet")
    }
}