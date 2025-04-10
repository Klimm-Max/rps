package de.klimmmax.rpsicq.service

import de.klimmmax.rpsicq.dto.BattleRequest
import de.klimmmax.rpsicq.dto.Position
import de.klimmmax.rpsicq.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID

class GameEngineBattleTest {

    private lateinit var gameEngine: GameEngine
    private lateinit var p1: Player
    private lateinit var p2: Player
    private lateinit var game: Game
    private lateinit var attacker: Tile
    private lateinit var defender: Tile

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
        fun roles(): List<Arguments> {
            return listOf(
                Arguments.of(Role.ROCK),
                Arguments.of(Role.SCISSORS),
                Arguments.of(Role.PAPER)
            )
        }
    }

    @BeforeEach
    fun setUp() {
        p1 = Player(UUID.randomUUID(), "Player 1")
        p2 = Player(UUID.randomUUID(), "Player 2")
        attacker = Tile(Position(0, 0), Figure(UUID.randomUUID(), ownerId = p1.id, role = Role.PAPER))
        defender = Tile(Position(0, 1), Figure(UUID.randomUUID(), ownerId = p2.id, role = Role.PAPER))
        game = Game(
            id = UUID.randomUUID(),
            players = Pair(p1, p2),
            currentPlayerTurn = p1.id,
            currentPhase = GAME_PHASE.BATTLE,
            battleState = BattleState(attacker, defender, mutableMapOf(p1.id to null, p2.id to null))
        )
        game.board[0][0] = attacker
        game.board[0][1] = defender

        gameEngine = GameEngine()
    }

    @Test
    fun `game not being in BATTLE phase is illegal`() {
        val battleRequest = BattleRequest(game.id, Role.ROCK)
        game.currentPhase = GAME_PHASE.PLAYER_TURN
        assertThrows<IllegalStateException> { gameEngine.processBattle(game, p1.id, battleRequest) }
    }

    @Test
    fun `battleState not set on game throws an error`() {
        val battleRequest = BattleRequest(game.id, Role.ROCK)
        game.battleState = null
        assertThrows<IllegalStateException> { gameEngine.processBattle(game, p1.id, battleRequest) }
    }

    @Test
    fun `attacker tile without a figure throws an error`() {
        val battleRequest = BattleRequest(game.id, Role.ROCK)
        game.battleState?.attacker?.figure = null
        assertThrows<IllegalStateException> { gameEngine.processBattle(game, p1.id, battleRequest) }
    }

    @Test
    fun `defender tile without a figure throws an error`() {
        val battleRequest = BattleRequest(game.id, Role.ROCK)
        game.battleState?.defender?.figure = null
        assertThrows<IllegalStateException> { gameEngine.processBattle(game, p1.id, battleRequest) }
    }

    @Test
    fun `another player trying to battle is illegal`() {
        val battleRequest = BattleRequest(game.id, Role.ROCK)
        assertThrows<IllegalStateException> { gameEngine.processBattle(game, UUID.randomUUID(), battleRequest) }
    }

    @Test
    fun `player already submitted his battle`() {
        val battleRequest = BattleRequest(game.id, Role.ROCK)
        game.battleState?.roles?.put(p1.id, Role.PAPER)
        assertThrows<IllegalStateException> { gameEngine.processBattle(game, p1.id, battleRequest) }
    }

    @Test
    fun `submitting as first player will return game and has battleState updated accordingly`() {
        val battleRequest = BattleRequest(game.id, Role.ROCK)

        val updatedGame = gameEngine.processBattle(game, p1.id, battleRequest)

        val updatedState = updatedGame.battleState!!
        assertEquals(Role.ROCK, updatedState.roles[p1.id], "Player 1's role should be stored")
        assertNull(updatedState.roles[p2.id], "Player 2 has not submitted a role yet")
        assertEquals(GAME_PHASE.BATTLE, updatedGame.currentPhase, "Game should remain in BATTLE phase")
        assertNotNull(updatedGame.battleState, "Battle state should still be active")
    }

    @ParameterizedTest
    @MethodSource("winningRoleCombinations")
    fun `battle is resolved when second player submits and attacker wins`(attackerRole: Role, defenderRole: Role) {
        game.battleState!!.roles[p1.id] = attackerRole

        val battleRequest = BattleRequest(game.id, defenderRole)
        val updatedGame = gameEngine.processBattle(game, p2.id, battleRequest)

        val updatedDefenderTile = updatedGame.board[defender.position.x][defender.position.y]
        assertEquals(p1.id, updatedDefenderTile.figure?.ownerId, "Attacker should now occupy defender's tile")
        val updatedAttackerTile = updatedGame.board[attacker.position.x][attacker.position.y]
        assertNull(updatedAttackerTile.figure, "Attacker's original tile should be empty")
        assertEquals(GAME_PHASE.PLAYER_TURN, updatedGame.currentPhase, "Game phase should return to PLAYER_TURN")
        assertNull(updatedGame.battleState, "Battle state should be cleared after resolution")
    }

    @ParameterizedTest
    @MethodSource("winningRoleCombinations")
    fun `defender wins the battle and attacker is removed`(defenderRole: Role, attackerRole: Role) {
        game.battleState!!.roles[p1.id] = attackerRole

        val battleRequest = BattleRequest(game.id, defenderRole)
        val updatedGame = gameEngine.processBattle(game, p2.id, battleRequest)

        val updatedDefenderTile = updatedGame.board[defender.position.x][defender.position.y]
        val updatedAttackerTile = updatedGame.board[attacker.position.x][attacker.position.y]
        assertEquals(p2.id, updatedDefenderTile.figure?.ownerId, "Defender should remain in place")
        assertNull(updatedAttackerTile.figure, "Attacker's tile should be empty after loss")
        assertEquals(GAME_PHASE.PLAYER_TURN, updatedGame.currentPhase)
        assertNull(updatedGame.battleState)
    }

    @ParameterizedTest
    @MethodSource("roles")
    fun `battle results in tie and both roles are reset`(role: Role) {
        game.battleState!!.roles[p1.id] = role

        val battleRequest = BattleRequest(game.id, role)
        val updatedGame = gameEngine.processBattle(game, p2.id, battleRequest)

        val state = updatedGame.battleState!!
        assertEquals(GAME_PHASE.BATTLE, updatedGame.currentPhase, "Game should remain in BATTLE phase")
        assertNotNull(state, "Battle state should still be active after tie")
        assertNull(state.roles[p1.id], "Attacker's role should be reset after tie")
        assertNull(state.roles[p2.id], "Defender's role should be reset after tie")
    }


}