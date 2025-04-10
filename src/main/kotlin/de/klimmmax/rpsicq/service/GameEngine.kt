package de.klimmmax.rpsicq.service

import de.klimmmax.rpsicq.dto.BattleRequest
import de.klimmmax.rpsicq.dto.MoveRequest
import de.klimmmax.rpsicq.dto.Position
import de.klimmmax.rpsicq.dto.SetupRequest
import de.klimmmax.rpsicq.model.*
import org.springframework.stereotype.Service
import java.util.UUID

import kotlin.math.abs
import kotlin.random.Random

@Service
class GameEngine {

    fun createInitialGame(p1: Player, p2: Player): Game {
        val game = Game(players = Pair(p1, p2))

        val p1Rows = listOf(0, 1)
        val p2Rows = listOf(4, 5)

        for (y in p1Rows) {
            for (x in 0 until game.board.size) {
                val figure = Figure(ownerId = p1.id)
                game.board[x][y].figure = figure
            }
        }

        for (y in p2Rows) {
            for (x in 0 until game.board.size) {
                val figure = Figure(ownerId = p2.id)
                game.board[x][y].figure = figure
            }
        }

        game.currentPlayerTurn = if (Random.nextBoolean()) game.players.first.id else game.players.second.id
        return game
    }

    fun processSetupPhase(game: Game, playerId: UUID, setup: SetupRequest): Game {
        check(game.currentPhase == GAME_PHASE.SETUP) { "Game must be in SETUP phase" }
        check(!isPositionIsInvalid(setup.king)) { "Position for king placement is not legal" }
        check(!isPositionIsInvalid(setup.trap)) { "Position for trap placement is not legal" }
        check(game.setupCompleted.containsKey(playerId)) { "You are not part of this game" }
        game.setupCompleted[playerId]?.let { check(!it) { "Player already completed setup step" } }
        check(setup.king != setup.trap) { "Placing trap and king on the same tile is illegal" }

        val kingTile = game.board[setup.king.x][setup.king.y]
        val trapTile = game.board[setup.trap.x][setup.trap.y]

        val kingFigure = kingTile.figure ?: throw IllegalStateException("placing the king on an empty tile is illegal")
        val trapFigure = trapTile.figure ?: throw IllegalStateException("placing the trap on an empty tile is illegal")

        check(kingFigure.ownerId == playerId && trapFigure.ownerId == playerId) {
            "Placing the king and/or trap for the opponent is illegal"
        }

        kingFigure.isKing = true
        trapFigure.isTrap = true

        game.setupCompleted[playerId] = true

        if (game.setupCompleted.values.all { it }) {
            game.currentPhase = GAME_PHASE.PLAYER_TURN
        }

        return game
    }

    fun processMove(game: Game, playerId: UUID, move: MoveRequest): Game {
        check(game.currentPhase == GAME_PHASE.PLAYER_TURN) { "Game must be in PLAYER_TURN phase" }
        check(isMoveValid(move)) { "Move is not legal" }

        val fromTile = game.board[move.from.x][move.from.y]
        val toTile = game.board[move.to.x][move.to.y]

        val figure = fromTile.figure ?: throw IllegalStateException("There is no piece to move")

        check(figure.ownerId == playerId) { "Not your piece" }

        check(!figure.isKing && !figure.isTrap) { "Its not allowed to move the king or trap" }

        val defender = toTile.figure

        if (defender != null) {
            check(defender.ownerId != playerId) { "You can not attack your own figures" }
            resolveAttackDuringPlayerTurn(game, figure, defender, fromTile, toTile)
        } else {
            toTile.figure = figure
        }

        if (game.currentPhase == GAME_PHASE.PLAYER_TURN) {
            fromTile.figure = null
            game.currentPlayerTurn =
                if (game.players.first.id == playerId) game.players.second.id else game.players.first.id
        }

        return game
    }

    fun processBattle(game: Game, playerId: UUID, battleRequest: BattleRequest): Game {
        check(game.currentPhase == GAME_PHASE.BATTLE) { "Game must be in BATTLE phase" }
        val state = game.battleState ?: throw IllegalStateException("No active battle state")
        val attackerFigure = state.attacker.figure ?: throw IllegalStateException("Attacker tile has no figure")
        val defenderFigure = state.defender.figure ?: throw IllegalStateException("Defender tile has no figure")
        check(state.roles.containsKey(playerId)) { "Player $playerId is not part of the battle" }
        check(state.roles[playerId] == null) { "Player $playerId has already submitted a role" }

        state.roles[playerId] = battleRequest.role
        // Wait for both players to submit their roles
        if (state.roles.values.any { it == null }) return game

        val attackerId = attackerFigure.ownerId
        val defenderId = defenderFigure.ownerId

        check(attackerId != defenderId) { "Attacker and defender must be different players" }

        val attackerChoice = state.roles[attackerId]!!
        val defenderChoice = state.roles[defenderId]!!
        when (resolveRPSBattle(attackerChoice, defenderChoice)) {
            "tie" -> {
                state.roles[attackerId] = null
                state.roles[defenderId] = null
            }

            "attacker" -> {
                game.board[state.defender.position.x][state.defender.position.y].figure = attackerFigure
                state.attacker.figure = null
                game.battleState = null
                game.currentPhase = GAME_PHASE.PLAYER_TURN
            }

            "defender" -> {
                game.board[state.attacker.position.x][state.attacker.position.y].figure = null
                game.battleState = null
                game.currentPhase = GAME_PHASE.PLAYER_TURN
            }
        }

        return game
    }


    private fun resolveAttackDuringPlayerTurn(
        game: Game,
        attacker: Figure,
        defender: Figure,
        fromTile: Tile,
        toTile: Tile
    ) {
        if (defender.isTrap) {
            return
        }

        attacker.isRevealed = true
        defender.isRevealed = true

        val attack =
            attacker.role ?: throw IllegalStateException("Attacker must have set a Role, otherwise it can't attack")

        val result = if (defender.isKing) {
            "won"
        } else {
            resolveRPSBattle(attack, defender.role!!)
        }

        when (result) {
            "attacker" -> {
                toTile.figure = attacker
                game.currentPhase = GAME_PHASE.PLAYER_TURN
            }

            "defender" -> game.currentPhase = GAME_PHASE.PLAYER_TURN // defender stays, fromTile will be cleared later
            "tie" -> {
                game.battleState =
                    BattleState(fromTile, toTile, mutableMapOf(attacker.ownerId to null, defender.ownerId to null))
                game.currentPhase = GAME_PHASE.BATTLE
            }

            "won" -> game.currentPhase = GAME_PHASE.END
        }
    }

    private fun resolveRPSBattle(attackerRole: Role, defenderRole: Role): String {
        return when {
            attackerRole == defenderRole -> "tie"
            attackerRole == Role.ROCK && defenderRole == Role.SCISSORS -> "attacker"
            attackerRole == Role.PAPER && defenderRole == Role.ROCK -> "attacker"
            attackerRole == Role.SCISSORS && defenderRole == Role.PAPER -> "attacker"
            else -> "defender"
        }
    }

    private fun isMoveValid(move: MoveRequest): Boolean {
        if (isPositionIsInvalid(move.from)) return false
        if (isPositionIsInvalid(move.to)) return false

        val dx = abs(move.from.x - move.to.x)
        val dy = abs(move.from.y - move.to.y)

        return (dx == 1 && dy == 0) || (dx == 0 && dy == 1)
    }

    private fun isPositionIsInvalid(pos: Position): Boolean {
        return pos.x < 0 || pos.y < 0 || pos.x > 6 || pos.y > 5
    }
}