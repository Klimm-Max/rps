package de.klimmmax.rpsicq.model

import java.util.*

data class Figure(
    val id: UUID = UUID.randomUUID(),
    val ownerId: UUID,
    var role: Role? = null,
    var isRevealed: Boolean = false,
    var isKing: Boolean = false,
    var isTrap: Boolean = false
)

enum class Role { ROCK, PAPER, SCISSORS }