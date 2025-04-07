package de.klimmmax.rpsicq.model

import java.util.*

data class Player(
    val id: UUID,
    val username: String,
    var state: PLAYER_STATE = PLAYER_STATE.IDLE
)

enum class PLAYER_STATE {
    IN_QUEUE,
    IN_GAME,
    IDLE
}