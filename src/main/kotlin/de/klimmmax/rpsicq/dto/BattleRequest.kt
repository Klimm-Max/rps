package de.klimmmax.rpsicq.dto

import de.klimmmax.rpsicq.model.Role
import java.util.UUID

data class BattleRequest(val gameId: UUID, val role: Role)
