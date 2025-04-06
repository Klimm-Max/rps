package de.klimmmax.rpsicq

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RpsicqApplication

fun main(args: Array<String>) {
	runApplication<RpsicqApplication>(*args)
}
