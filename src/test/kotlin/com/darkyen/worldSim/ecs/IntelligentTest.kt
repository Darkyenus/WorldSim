package com.darkyen.worldSim.ecs

import com.darkyen.worldSim.EntityChunkPopulator
import com.darkyen.worldSim.NoiseWorldGenerator
import com.darkyen.worldSim.SimulationSpeedRegulator
import com.darkyen.worldSim.ai.loop
import com.github.antag99.retinazer.Engine
import com.github.antag99.retinazer.resolvers.SimpleWireResolver
import org.junit.jupiter.api.Test
import kotlin.coroutines.coroutineContext

/**
 *
 */
class IntelligentTest {

	@Test
	fun test(){
		val simulationSpeedRegulator = SimulationSpeedRegulator()
		simulationSpeedRegulator.simulationDelta = 0.2f
		val engine = Engine(COMPONENT_DOMAIN,
				SimpleWireResolver(simulationSpeedRegulator),

				World(NoiseWorldGenerator(), EntityChunkPopulator),
				PathFinder(),
				AgentSpatialLookup(),

				IntelligentS())

		val rabbit = engine.createEntity()
		engine.getMapper(AgentC::class.java).add(rabbit, AgentC(false))
		engine.getMapper(PositionC::class.java).add(rabbit, PositionC())
		engine.getMapper(AgentSpeechC::class.java).add(rabbit, AgentSpeechC())
		engine.getMapper(IntelligentC::class.java).add(rabbit, IntelligentC {
			with(coroutineContext[AIContext]!!) {
				loop {
					println("I am a rabbit! The time is $currentTimeMs!")
					delay(AgentActivity.SLEEPING, 100L)
				}
			}
		})

		for (i in 0 until 10) {
			println("--------------- $i --------------")
			engine.update()
		}
		println("-------------- deletion ------------")
		engine.destroyEntity(rabbit)

		for (i in 10 until 15) {
			println("--------------- $i --------------")
			engine.update()
		}
	}

}