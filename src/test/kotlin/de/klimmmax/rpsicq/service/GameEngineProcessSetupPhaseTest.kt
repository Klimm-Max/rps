package de.klimmmax.rpsicq.service

import de.klimmmax.rpsicq.dto.Position
import de.klimmmax.rpsicq.dto.SetupRequest
import de.klimmmax.rpsicq.model.GAME_PHASE
import de.klimmmax.rpsicq.model.Game
import de.klimmmax.rpsicq.model.Player
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*


class GameEngineProcessSetupPhaseTest {
    private lateinit var gameEngine: GameEngine
    private lateinit var p1: Player
    private lateinit var p2: Player
    private lateinit var game: Game

    @BeforeEach
    fun setUp() {
        p1 = Player(UUID.randomUUID(), "Player 1")
        p2 = Player(UUID.randomUUID(), "Player 2")
        gameEngine = GameEngine()
        game = gameEngine.createInitialGame(p1, p2)
    }

    @Test
    fun `game must be in setup phase`() {
        game.currentPhase = GAME_PHASE.PLAYER_TURN
        val setup = SetupRequest(game.id, king = Position(0, 0), trap = Position(0, 1))

        assertThrows<IllegalStateException> { gameEngine.processSetupPhase(game, p1.id, setup) }
    }

    @Test
    fun `king position outside of board is illegal`() {
        val setup = SetupRequest(game.id, king = Position(-1, -1), trap = Position(0, 1))
        assertThrows<IllegalStateException> { gameEngine.processSetupPhase(game, p1.id, setup) }
    }

    @Test
    fun `trap position outside of board is illegal`() {
        val setup = SetupRequest(game.id, king = Position(2, 2), trap = Position(10, 10))
        assertThrows<IllegalStateException> { gameEngine.processSetupPhase(game, p1.id, setup) }
    }

    @Test
    fun `anonymous player sending the request is illegal`() {
        val setup = SetupRequest(game.id, king = Position(0, 0), trap = Position(0, 1))
        assertThrows<IllegalStateException> { gameEngine.processSetupPhase(game, UUID.randomUUID(), setup) }
    }

    @Test
    fun `sending a setup request multiple times is illegal`() {
        val setup = SetupRequest(game.id, king = Position(0, 0), trap = Position(0, 1))
        game.setupCompleted[p1.id] = true
        assertThrows<IllegalStateException> { gameEngine.processSetupPhase(game, p1.id, setup) }
    }

    @Test
    fun `placing trap and king on the same tile is illegal`() {
        val setup = SetupRequest(game.id, king = Position(0, 0), trap = Position(0, 0))
        assertThrows<IllegalStateException> { gameEngine.processSetupPhase(game, p1.id, setup) }
    }

    @Test
    fun `placing the king on an empty tile is illegal`() {
        val setup = SetupRequest(game.id, king = Position(3, 3), trap = Position(0, 1))
        assertThrows<IllegalStateException> { gameEngine.processSetupPhase(game, p1.id, setup) }
    }

    @Test
    fun `placing the trap on an empty tile is illegal`() {
        val setup = SetupRequest(game.id, king = Position(0, 0), trap = Position(3, 3))
        assertThrows<IllegalStateException> { gameEngine.processSetupPhase(game, p1.id, setup) }
    }

    @Test
    fun `placing the king on an opponents tile is illegal`() {
        val setup = SetupRequest(game.id, king = Position(0, 4), trap = Position(0, 1))
        assertThrows<IllegalStateException> { gameEngine.processSetupPhase(game, p1.id, setup) }
    }

    @Test
    fun `placing the trap on an opponents tile is illegal`() {
        val setup = SetupRequest(game.id, king = Position(0, 0), trap = Position(4, 4))
        assertThrows<IllegalStateException> { gameEngine.processSetupPhase(game, p1.id, setup) }
    }

    @Test
    fun `a correct setup while player2 still has to do his setup`() {
        val setup = SetupRequest(game.id, king = Position(0, 0), trap = Position(0, 1))

        game = gameEngine.processSetupPhase(game, p1.id, setup)

        val king = game.board[0][0].figure
        assertNotNull(king, "King Figure must not be null")
        with(king!!) {
            assertTrue(isKing, "King must be correctly set")
            assertFalse(isTrap, "Trap value on the King tile must be false")
            assertEquals(p1.id, ownerId, "King owner-id must match player 1")
        }
        val trap = game.board[0][1].figure
        assertNotNull(trap, "Trap Figure must not be null")
        with(trap!!) {
            assertTrue(isTrap, "Trap must be correctly set")
            assertFalse(isKing, "King value on the trap tile must be false")
            assertEquals(p1.id, ownerId, "Trap owner-id must match player 1")
        }
        game.setupCompleted[p1.id]?.let { assertTrue(it) }
        game.setupCompleted[p2.id]?.let { assertFalse(it) }
        assertEquals(GAME_PHASE.SETUP, game.currentPhase, "phase must remain in SETUP while player 2 hasn't completed the setup")
    }

    @Test
    fun `a correct setup while player2 already completed his setup`() {
        val setup = SetupRequest(game.id, king = Position(4, 4), trap = Position(4, 5))

        game.setupCompleted[p1.id] = true
        game = gameEngine.processSetupPhase(game, p2.id, setup)

        val king = game.board[4][4].figure
        assertNotNull(king, "King Figure must not be null")
        with(king!!) {
            assertTrue(isKing, "King must be correctly set")
            assertFalse(isTrap, "Trap value on the King tile must be false")
            assertEquals(p2.id, ownerId, "King owner-id must match player 2")
        }
        val trap = game.board[4][5].figure
        assertNotNull(trap, "Trap Figure must not be null")
        with(trap!!) {
            assertTrue(isTrap, "Trap must be correctly set")
            assertFalse(isKing, "King value on the trap tile must be false")
            assertEquals(p2.id, ownerId, "Trap owner-id must match player 2")
        }
        game.setupCompleted[p1.id]?.let { assertTrue(it) }
        game.setupCompleted[p2.id]?.let { assertTrue(it) }
        assertEquals(GAME_PHASE.COIN_FLIP, game.currentPhase, "phase must be in COIN_FLIP once setup phase is completed")
    }
}