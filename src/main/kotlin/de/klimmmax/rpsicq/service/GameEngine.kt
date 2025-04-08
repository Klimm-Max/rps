package de.klimmmax.rpsicq.service

import de.klimmmax.rpsicq.dto.MoveRequest
import de.klimmmax.rpsicq.dto.Position
import de.klimmmax.rpsicq.model.*
import org.springframework.stereotype.Service
import java.util.*
import kotlin.math.abs

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

        return game
    }

    // TODO the playerId needs to be extracted via Spring Security Websocket Session
    fun processMove(game: Game, playerId: UUID, move: MoveRequest): Game {
        check(isMoveValid(move)) { throw IllegalStateException("Move is not legal") }

        val fromTile = game.board[move.from.x][move.from.y]
        val toTile = game.board[move.to.x][move.to.y]

        val figure = fromTile.figure ?: throw IllegalStateException("There is no piece to move")

        check(figure.ownerId == playerId) { throw IllegalStateException("Not your piece") }

        check(!figure.isKing && !figure.isTrap) { throw IllegalStateException("Its not allowed to move the king or trap") }

        val defender = toTile.figure

        if (defender != null) {
            resolveBattle(game, figure, defender, toTile)
        } else {
            toTile.figure = figure
        }

        if (game.currentPhase == GAME_PHASE.PLAYER_TURN) {
            fromTile.figure = null
            game.currentPlayerTurn = if (game.players.first.id == playerId) game.players.second.id else game.players.first.id
        }

        return game
    }

    private fun resolveBattle(game: Game, attacker: Figure, defender: Figure, toTile: Tile) {
        if (defender.isTrap) {
            return
        }

        attacker.isRevealed = true
        defender.isRevealed = true

        val attack = attacker.role
        val defend = defender.role

        val result = when {
            attack == defend -> "tie"
            attack == Role.ROCK && defend == Role.SCISSORS -> "attacker"
            attack == Role.PAPER && defend == Role.ROCK -> "attacker"
            attack == Role.SCISSORS && defend == Role.PAPER -> "attacker"
            defender.isKing -> "won"
            else -> "defender"
        }

        when(result) {
            "attacker" -> {
                toTile.figure = attacker
                game.currentPhase = GAME_PHASE.PLAYER_TURN
            }
            "defender" -> game.currentPhase = GAME_PHASE.PLAYER_TURN // defender just stays, fromTile will be cleared later in game loop
            "tie" -> game.currentPhase = GAME_PHASE.BATTLE
            "won" -> game.currentPhase = GAME_PHASE.END
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