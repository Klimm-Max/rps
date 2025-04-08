package de.klimmmax.rpsicq.dto

import java.util.*

data class MoveRequest(val gameId: UUID, val from: Position, val to: Position)
