package de.klimmmax.rpsicq.model

import de.klimmmax.rpsicq.dto.Position

data class Tile(val position: Position, var figure: Figure? = null)
