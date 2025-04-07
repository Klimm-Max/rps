package de.klimmmax.rpsicq.service

import de.klimmmax.rpsicq.dto.MoveRequest
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

        val fromTile = game.board[move.fromX][move.fromY]
        val toTile = game.board[move.toX][move.toY]

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

    /**
     * Player moves are only legal if they are either one step horizontal or vertical while remain inside the board
     * */
    private fun isMoveValid(move: MoveRequest): Boolean {
        val (fromX, fromY, toX, toY) = listOf(move.fromX, move.fromY, move.toX, move.toY)

        if (fromX < 0 || fromY < 0 || toX < 0 || toY < 0) return false
        if (fromX > 6 || fromY > 5 || toX > 6 || toY > 5) return false

        val dx = abs(fromX - toX)
        val dy = abs(fromY - toY)

        return (dx == 1 && dy == 0) || (dx == 0 && dy == 1)
    }
}