package de.klimmmax.rpsicq.dto

import java.util.*

data class SetupRequest(val gameId: UUID, val king: Position, val trap: Position)

data class Position(val x: Int, val y: Int)