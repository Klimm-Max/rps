package de.klimmmax.rpsicq.service

import de.klimmmax.rpsicq.dto.MoveRequest
import de.klimmmax.rpsicq.model.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.*

@SpringBootTest
class GameEngineTest {

    @Autowired
    lateinit var gameEngine: GameEngine

    lateinit var p1: Player
    lateinit var p2: Player
    lateinit var game: Game

    companion object {
        @JvmStatic
        fun winningRoleCombinations(): List<Arguments> {
            return listOf(
                Arguments.of(Role.ROCK, Role.SCISSORS),
                Arguments.of(Role.SCISSORS, Role.PAPER),
                Arguments.of(Role.PAPER, Role.ROCK)
            )
        }
        @JvmStatic
        fun illegalBoardCoordinates(): List<Arguments> {
            return listOf(
                Arguments.of(MoveRequest(UUID.randomUUID(), 0, 0, -1, 0)),
                Arguments.of(MoveRequest(UUID.randomUUID(), 0, 0, 0, -1)),
                Arguments.of(MoveRequest(UUID.randomUUID(), 0, 0, 1, 1)),
                Arguments.of(MoveRequest(UUID.randomUUID(), -1, 0, 0, 0)),
                Arguments.of(MoveRequest(UUID.randomUUID(), 0, -1, 0, -1)),
                Arguments.of(MoveRequest(UUID.randomUUID(), 6, 5, 6, 6)),
                Arguments.of(MoveRequest(UUID.randomUUID(), 6, 0, 6, -1)),
                Arguments.of(MoveRequest(UUID.randomUUID(), 0, 0, 12, 1)),
            )
        }
    }

    @BeforeEach
    fun setUp() {
        p1 = Player(UUID.randomUUID(), "Player 1", PLAYER_STATE.IN_GAME)
        p2 = Player(UUID.randomUUID(), "Player 2", PLAYER_STATE.IN_GAME)
        game = Game(
            id = UUID.randomUUID(),
            players = Pair(p1, p2),
            currentPlayerTurn = p1.id,
            currentPhase = GAME_PHASE.PLAYER_TURN
        )
    }

    @Test
    fun `initial game setup places figures correctly`() {
        val newGame = gameEngine.createInitialGame(p1, p2)

        val board = newGame.board
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

        assertEquals(GAME_PHASE.SETUP, newGame.currentPhase, "After initialising, we must be in the SETUP phase")
        val totalFigures = board.flatten().count { it.figure != null }
        assertEquals(28, totalFigures, "Each player should have 14 figures (2 rows of 7)")
        val totalNumberOfKingsAndTraps = board.flatten().mapNotNull { it.figure }.count { it.isTrap || it.isKing }
        assertEquals(0, totalNumberOfKingsAndTraps, "There must be no king or trap placed just yet")
    }


    @Test
    fun `a single legal move without any figure interaction`() {
        val figure = Figure(ownerId = p1.id, role = Role.ROCK)
        val startTile = game.board[0][0]
        startTile.figure = figure

        val moveRequest = MoveRequest(
            gameId = game.id, fromX = 0, fromY = 0, toX = 0, toY = 1
        )

        gameEngine.processMove(game, p1.id, moveRequest)

        val oldTile = game.board[0][0]
        val newTile = game.board[0][1]

        assertNull(oldTile.figure, "The figure should have moved away from the original tile")
        assertNotNull(newTile.figure, "The figure should be on the destination tile")
        assertEquals(newTile.figure, figure)
        assertEquals(p1.id, newTile.figure!!.ownerId, "The figure's owner should remain Player 1")
    }

    @ParameterizedTest
    @MethodSource("illegalBoardCoordinates")
    fun `passing illegal board coordinates`(moveRequest: MoveRequest) {
        val figure = Figure(ownerId = p1.id, role = Role.ROCK)
        val startTile = game.board[0][0]
        startTile.figure = figure


        assertThrows<IllegalStateException> {
            gameEngine.processMove(game, p1.id, moveRequest.copy(gameId = game.id))
        }
    }

    @ParameterizedTest
    @MethodSource("winningRoleCombinations")
    fun battleWithAttackerWinning(attackerRole: Role, defenderRole: Role) {
        val (attacker, _) = setupAttackerAndDefenderOnBoard(attackerRole, defenderRole)

        val moveRequest = MoveRequest(
            gameId = game.id, fromX = 0, fromY = 0, toX = 1, toY = 0
        )

        gameEngine.processMove(game, p1.id, moveRequest)

        val oldTile = game.board[0][0]
        val newTile = game.board[1][0]

        assertNull(oldTile.figure, "The figure should have moved away from the original tile")
        assertNotNull(newTile.figure, "The figure should be on the destination tile")
        assertEquals(newTile.figure, attacker)
        assertEquals(p1.id, newTile.figure!!.ownerId, "The attacking figure still remains as player 1")
        assertEquals(game.currentPhase, GAME_PHASE.PLAYER_TURN)
        assertEquals(p2.id, game.currentPlayerTurn, "After playing the attack turn, it's Player 2s turn")
    }

    @ParameterizedTest
    @MethodSource("winningRoleCombinations")
    fun battleWithDefenderWinning(defenderRole: Role, attackerRole: Role) {
        val (_, defender) = setupAttackerAndDefenderOnBoard(attackerRole, defenderRole)

        val moveRequest = MoveRequest(
            gameId = game.id, fromX = 0, fromY = 0, toX = 1, toY = 0
        )

        gameEngine.processMove(game, p1.id, moveRequest)

        val oldTile = game.board[0][0]
        val newTile = game.board[1][0]

        assertNull(oldTile.figure, "The attacker lost the battle, therefore the oldTile is cleaned")
        assertNotNull(newTile.figure, "The defender is still standing on the defending Tile")
        assertEquals(newTile.figure, defender)
        assertEquals(p2.id, newTile.figure!!.ownerId, "The defending figure still remains to player 2")
        assertEquals(game.currentPhase, GAME_PHASE.PLAYER_TURN)
        assertEquals(p2.id, game.currentPlayerTurn, "After playing the attack turn, it's Player 2s turn")
    }

    @ParameterizedTest
    @MethodSource("winningRoleCombinations")
    fun trapIsAlwaysWinningAgainstAttackers(attackerRole: Role, unused: Role) {
        val (_, defender) = setupAttackerAndDefenderOnBoard(attackerRole, unused)

        defender.isTrap = true

        val moveRequest = MoveRequest(
            gameId = game.id, fromX = 0, fromY = 0, toX = 1, toY = 0
        )

        gameEngine.processMove(game, p1.id, moveRequest)

        val oldTile = game.board[0][0]
        val newTile = game.board[1][0]

        assertNull(oldTile.figure, "The attacker fell for the trap, therefore the oldTile is cleaned")
        assertNotNull(newTile.figure, "The trap is still standing on the defending Tile")
        assertEquals(newTile.figure, defender)
        assertEquals(p2.id, newTile.figure!!.ownerId, "The defending figure still remains to player 2")
        assertEquals(p2.id, game.currentPlayerTurn, "After playing the attack turn, it's Player 2s turn")
    }

    @ParameterizedTest
    @MethodSource("winningRoleCombinations")
    fun anyAttackerAgainstKingWinsTheGame(attackerRole: Role, unused: Role) {
        val (_, defender) = setupAttackerAndDefenderOnBoard(attackerRole, null)

        defender.isKing = true

        val moveRequest = MoveRequest(
            gameId = game.id, fromX = 0, fromY = 0, toX = 1, toY = 0
        )

        gameEngine.processMove(game, p1.id, moveRequest)

        assertEquals(GAME_PHASE.END, game.currentPhase, "The game is in the final END state")
        assertEquals(p1.id, game.currentPlayerTurn, "The player who started the attack on the king, wins")
    }

    private fun setupAttackerAndDefenderOnBoard(attackerRole: Role, defenderRole: Role?): Pair<Figure, Figure> {
        val attacker = Figure(ownerId = p1.id, role = attackerRole)
        val startTile = game.board[0][0]
        startTile.figure = attacker

        val defender = Figure(ownerId = p2.id, role = defenderRole)
        val defenderTile = game.board[1][0]
        defenderTile.figure = defender

        return Pair(attacker, defender)
    }
}