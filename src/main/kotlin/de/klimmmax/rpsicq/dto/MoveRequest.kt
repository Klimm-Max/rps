package de.klimmmax.rpsicq.dto

import java.util.*

data class MoveRequest(
    val gameId: UUID,
    val fromX: Int,
    val fromY: Int,
    val toX: Int,
    val toY: Int
)
