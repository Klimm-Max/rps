package de.klimmmax.rpsicq.service

import de.klimmmax.rpsicq.dto.MoveRequest
import de.klimmmax.rpsicq.model.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.*

class GameEngineProcessMoveTest {

    private lateinit var gameEngine: GameEngine
    private lateinit var p1: Player
    private lateinit var p2: Player
    private lateinit var game: Game

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
        gameEngine = GameEngine()
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

        Assertions.assertNull(oldTile.figure, "The figure should have moved away from the original tile")
        Assertions.assertNotNull(newTile.figure, "The figure should be on the destination tile")
        Assertions.assertEquals(newTile.figure, figure)
        Assertions.assertEquals(p1.id, newTile.figure!!.ownerId, "The figure's owner should remain Player 1")
    }

    @Test
    fun `moving the trap is illegal`() {
        val figure = Figure(ownerId = p1.id, isTrap = true)
        val startTile = game.board[0][0]
        startTile.figure = figure

        val moveRequest = MoveRequest(gameId = game.id, fromX = 0, fromY = 0, toX = 0, toY = 1)

        assertThrows<IllegalStateException> {
            gameEngine.processMove(game, p1.id, moveRequest)
        }
    }

    @Test
    fun `moving the king is illegal`() {
        val figure = Figure(ownerId = p1.id, isKing = true)
        val startTile = game.board[0][0]
        startTile.figure = figure

        val moveRequest = MoveRequest(gameId = game.id, fromX = 0, fromY = 0, toX = 0, toY = 1)

        assertThrows<IllegalStateException> {
            gameEngine.processMove(game, p1.id, moveRequest)
        }
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
    fun `battle where the attacker wins`(attackerRole: Role, defenderRole: Role) {
        val (attacker, _) = setupAttackerAndDefenderOnBoard(attackerRole, defenderRole)

        val moveRequest = MoveRequest(gameId = game.id, fromX = 0, fromY = 0, toX = 1, toY = 0)

        gameEngine.processMove(game, p1.id, moveRequest)

        val oldTile = game.board[0][0]
        val newTile = game.board[1][0]

        Assertions.assertNull(oldTile.figure, "The figure should have moved away from the original tile")
        Assertions.assertNotNull(newTile.figure, "The figure should be on the destination tile")
        Assertions.assertEquals(newTile.figure, attacker)
        Assertions.assertEquals(p1.id, newTile.figure!!.ownerId, "The attacking figure still remains as player 1")
        Assertions.assertEquals(game.currentPhase, GAME_PHASE.PLAYER_TURN)
        Assertions.assertEquals(p2.id, game.currentPlayerTurn, "After playing the attack turn, it's Player 2s turn")
    }

    @ParameterizedTest
    @MethodSource("winningRoleCombinations")
    fun `battle where the defender wins`(defenderRole: Role, attackerRole: Role) {
        val (_, defender) = setupAttackerAndDefenderOnBoard(attackerRole, defenderRole)

        val moveRequest = MoveRequest(gameId = game.id, fromX = 0, fromY = 0, toX = 1, toY = 0)

        gameEngine.processMove(game, p1.id, moveRequest)

        val oldTile = game.board[0][0]
        val newTile = game.board[1][0]

        Assertions.assertNull(oldTile.figure, "The attacker lost the battle, therefore the oldTile is cleaned")
        Assertions.assertNotNull(newTile.figure, "The defender is still standing on the defending Tile")
        Assertions.assertEquals(newTile.figure, defender)
        Assertions.assertEquals(p2.id, newTile.figure!!.ownerId, "The defending figure still remains to player 2")
        Assertions.assertEquals(game.currentPhase, GAME_PHASE.PLAYER_TURN)
        Assertions.assertEquals(p2.id, game.currentPlayerTurn, "After playing the attack turn, it's Player 2s turn")
    }

    @ParameterizedTest
    @MethodSource("winningRoleCombinations")
    fun `the trap is always removing the attacker while staying intact`(attackerRole: Role, unused: Role) {
        val (_, defender) = setupAttackerAndDefenderOnBoard(attackerRole, unused)

        defender.isTrap = true

        val moveRequest = MoveRequest(gameId = game.id, fromX = 0, fromY = 0, toX = 1, toY = 0)

        gameEngine.processMove(game, p1.id, moveRequest)

        val oldTile = game.board[0][0]
        val newTile = game.board[1][0]

        Assertions.assertNull(oldTile.figure, "The attacker fell for the trap, therefore the oldTile is cleaned")
        Assertions.assertNotNull(newTile.figure, "The trap is still standing on the defending Tile")
        Assertions.assertEquals(newTile.figure, defender)
        Assertions.assertEquals(p2.id, newTile.figure!!.ownerId, "The defending figure still remains to player 2")
        Assertions.assertEquals(p2.id, game.currentPlayerTurn, "After playing the attack turn, it's Player 2s turn")
    }

    @ParameterizedTest
    @MethodSource("winningRoleCombinations")
    fun `any attacker against the king wins the game`(attackerRole: Role, unused: Role) {
        val (_, defender) = setupAttackerAndDefenderOnBoard(attackerRole, null)

        defender.isKing = true

        val moveRequest = MoveRequest(gameId = game.id, fromX = 0, fromY = 0, toX = 1, toY = 0)

        gameEngine.processMove(game, p1.id, moveRequest)

        Assertions.assertEquals(GAME_PHASE.END, game.currentPhase, "The game is in the final END state")
        Assertions.assertEquals(p1.id, game.currentPlayerTurn, "The player who started the attack on the king, wins")
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