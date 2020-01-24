package com.darkyen.worldSim.ecs

import com.github.antag99.retinazer.Component
import com.github.antag99.retinazer.Mapper
import com.github.antag99.retinazer.Wire
import com.github.antag99.retinazer.systems.EntityProcessorSystem

/**
 * Entity with this component will disappear after some time
 */
class DecayC(var remainingTime:Float) : Component

class DecayS : EntityProcessorSystem(COMPONENT_DOMAIN.familyWith(DecayC::class.java)) {

	@Wire
	private lateinit var decayC: Mapper<DecayC>
	@Wire
	private lateinit var simulationClock : SimulationSpeed

	override fun update(delta: Float) {
		super.update(simulationClock.simulationDelta)
	}

	override fun process(entity: Int, delta: Float) {
		val decay = decayC[entity]!!

		decay.remainingTime -= delta
		if (decay.remainingTime <= 0f) {
			engine.destroyEntity(entity)
		}
	}

}