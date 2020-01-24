package com.darkyen.worldSim.ecs

import com.github.antag99.retinazer.Mapper
import com.github.antag99.retinazer.Wire
import com.github.antag99.retinazer.systems.EntityProcessorSystem
import kotlin.random.Random

/**
 *
 */
class AgentNeedS : EntityProcessorSystem(COMPONENT_DOMAIN.familyWith(AgentC::class.java)) {

	@Wire
	private lateinit var agentC : Mapper<AgentC>
	@Wire
	private lateinit var renderC : Mapper<RenderC>
	@Wire
	private lateinit var decayC : Mapper<DecayC>
	@Wire
	private lateinit var positionC : Mapper<PositionC>
	@Wire
	private lateinit var simulationClock : SimulationSpeed

	private var dayProgress = 0f

	override fun update(realDelta: Float) {
		val delta = simulationClock.simulationDelta
		dayProgress += delta
		while (dayProgress >= HOUR_LENGTH_IN_REAL_SECONDS) {
			dayProgress -= HOUR_LENGTH_IN_REAL_SECONDS
			super.update(HOUR_LENGTH_IN_REAL_SECONDS)
		}
	}

	private fun ByteArray.update(attribute:AgentAttribute, tendency:Float) {
		val change = (tendency + Random.nextFloat()).toInt()
		this[attribute] = this[attribute] + change
	}

	private fun ByteArray.shouldDie(attribute:AgentAttribute, deathThreshold:Byte):Boolean {
		val value = this[attribute]
		if (value >= deathThreshold) {
			return false
		}
		val direness = (value - attribute.min) / (deathThreshold - attribute.min).toFloat()
		val roll = Random.nextFloat()
		if (roll < direness) {
			// Uh oh, time to die, or not?
			if (Random.nextInt(110) > this[AgentAttribute.ENDURANCE]) {
				// Time to die.
				return true
			}
		}
		return false
	}

	override fun process(entity: Int, delta: Float) {
		val agent = agentC[entity]

		val attributes = agent.attributes
		// Check if should die
		val shouldDie = attributes.shouldDie(AgentAttribute.HEALTH, 10)
				|| attributes.shouldDie(AgentAttribute.THIRST, -50)
				|| attributes.shouldDie(AgentAttribute.HUNGER, -50)

		if (shouldDie) {
			// Kill
			agentC.remove(entity)
			positionC[entity].speed = 0f
			renderC[entity].sprite = 103
			decayC.add(entity, DecayC(300_000f))
			return
		}

		val activity = agent.activity
		attributes.update(AgentAttribute.HEALTH, if (activity == AgentActivity.SLEEPING) HEALTH_TENDENCY_SLEEP else HEALTH_TENDENCY)
		attributes.update(AgentAttribute.THIRST, if (activity == AgentActivity.DRINKING) 0f else THIRST_TENDENCY)
		attributes.update(AgentAttribute.HUNGER, if (activity == AgentActivity.EATING) 0f else if (activity == AgentActivity.WALKING || activity == AgentActivity.PANICKING) HUNGER_TENDENCY_WALKING else HUNGER_TENDENCY)
		attributes.update(AgentAttribute.SLEEP, if (activity == AgentActivity.SLEEPING) 0f else SLEEP_TENDENCY)
		attributes.update(AgentAttribute.ALERTNESS, if (activity == AgentActivity.SLEEPING) ALERTNESS_TENDENCY_SLEEP else ALERTNESS_TENDENCY)
		attributes.update(AgentAttribute.SOCIAL, if (activity == AgentActivity.SLEEPING) SOCIAL_TENDENCY_SLEEP else SOCIAL_TENDENCY)
	}

	private companion object {
		// Default decay tendencies
		/* These values will move by this much each hour.
		 * They will be rounded in random direction if fractional, with bias to closer integer. */

		const val HEALTH_TENDENCY = 1f
		const val HEALTH_TENDENCY_SLEEP = 2f

		const val HUNGER_TENDENCY = -0.83f // ~10 days without food
		const val HUNGER_TENDENCY_WALKING = -2f

		const val THIRST_TENDENCY = -4f // ~2 days without water when doing nothing

		const val SLEEP_TENDENCY = -4f // ~2 days without sleep, with sleep deprivation after 1 day

		const val ALERTNESS_TENDENCY = -8f
		const val ALERTNESS_TENDENCY_SLEEP = -10f

		const val SOCIAL_TENDENCY = -1f
		const val SOCIAL_TENDENCY_SLEEP = -0.5f
	}
}