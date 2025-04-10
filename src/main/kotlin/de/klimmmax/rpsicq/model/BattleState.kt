package de.klimmmax.rpsicq.model

import java.util.*

data class BattleState(val attacker: Tile, val defender: Tile, val roles: MutableMap<UUID, Role?>)
