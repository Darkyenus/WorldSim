package com.darkyen.worldSim.ecs

import com.darkyen.worldSim.SimulationSpeedRegulator
import com.darkyen.worldSim.ai.AIContext
import com.darkyen.worldSim.ai.doActivityDelay
import com.darkyen.worldSim.util.Vec2
import com.github.antag99.retinazer.Component
import com.github.antag99.retinazer.EntitySystem
import com.github.antag99.retinazer.Mapper
import com.github.antag99.retinazer.Wire
import kotlinx.coroutines.delay

/** Component on entities, that are saying something right now. */
class AgentSpeechC : Component.Pooled {
	/** What is the question/info about */
	var saying:MemoryType = MemoryType.NO_MEMORY
	/** [Vec2.NULL] = asking, otherwise telling */
	var sayingPos:Vec2 = Vec2.NULL
}

/**
 *
 */
class AgentSpeechS : EntitySystem(COMPONENT_DOMAIN.familyWith(PositionC::class.java, AgentSpeechC::class.java)) {

	@Wire
	lateinit var world: World
	@Wire
	private lateinit var simulationClock : SimulationSpeedRegulator
	@Wire
	lateinit var positionC: Mapper<PositionC>
	@Wire
	lateinit var agentC: Mapper<AgentC>
	@Wire
	lateinit var agentSpeechC: Mapper<AgentSpeechC>
}

/**
 * Ask question or say some information.
 *
 * @param type what is the info about
 * @param pos information about [type] or, if this is a question, [Vec2.NULL]
 */
suspend fun AIContext.say(type:MemoryType, pos: Vec2, durationMs:Long = 3000, socialReward:Int = 1) {
	val entity = entity
	val agentSpeechC = agentS.speech.agentSpeechC
	if (agentSpeechC.has(entity)) {
		// May happen if asking too quickly, and the old speech hasn't flushed yet
		delay(100)
	}
	agentSpeechC.create(entity).apply {
		this.saying = type
		this.sayingPos = pos
	}
	try {
		doActivityDelay(if (pos == Vec2.NULL) AgentActivity.ASKING else AgentActivity.RESPONDING , durationMs)
		val agent = agent
		val attributes = agent.attributes
		attributes[AgentAttribute.SOCIAL] = attributes[AgentAttribute.SOCIAL] + socialReward
	} finally {
		agentSpeechC.remove(entity)
	}
}