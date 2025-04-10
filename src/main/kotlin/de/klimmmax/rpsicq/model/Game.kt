package de.klimmmax.rpsicq.model

import de.klimmmax.rpsicq.dto.Position
import java.util.UUID

data class Game(
    val id: UUID = UUID.randomUUID(),
    val players: Pair<Player, Player>,
    val board: Array<Array<Tile>> = Array(7) { x ->
        Array(6) { y -> Tile(Position(x, y)) }
    },
    var currentPhase: GAME_PHASE = GAME_PHASE.SETUP,
    var currentPlayerTurn: UUID? = null,
    val setupCompleted: MutableMap<UUID, Boolean> = mutableMapOf(
        players.first.id to false,
        players.second.id to false
    ),
    var battleState: BattleState? = null
)

enum class GAME_PHASE { SETUP, PLAYER_TURN, BATTLE, END }