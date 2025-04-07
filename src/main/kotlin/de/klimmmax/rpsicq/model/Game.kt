package de.klimmmax.rpsicq.model

import java.util.UUID

data class Game(
    val id: UUID = UUID.randomUUID(),
    val players: Pair<Player, Player>,
    val board: Array<Array<Tile>> = Array(7) { x ->
        Array(7) { y -> Tile(x, y) }
    },
    var currentPhase: GAME_PHASE = GAME_PHASE.SETUP,
    var currentPlayerTurn: UUID? = null
)

enum class GAME_PHASE { SETUP, COIN_FLIP, PLAYER_TURN, BATTLE, END }