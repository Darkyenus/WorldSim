package com.darkyen.worldSim.ai

import com.darkyen.worldSim.ecs.AgentS.Companion.position
import com.darkyen.worldSim.ecs.AgentS.Companion.walkTo
import com.darkyen.worldSim.util.forDirections
import kotlinx.coroutines.delay

/**
 *
 */
suspend fun simpleBrain() {
	// Walking round and round
	var successes = 0
	forDirections { dir ->
		if (walkTo(position() + dir * 10)) {
			successes++
		}
	}
	if (successes == 0) {
		delay(1000)
	}
}