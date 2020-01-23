package com.darkyen.worldSim.ai

import com.darkyen.worldSim.ecs.AgentC
import com.darkyen.worldSim.ecs.AgentS
import com.github.antag99.retinazer.util.Bag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToLong

/**
 * Runs braainss.
 */
class AICoroutineManager {

	private val dispatcher = AICoroutineExecutorService()
	private val coroutineDispatcher = dispatcher.asCoroutineDispatcher()
	private val coroutineScope = CoroutineScope(coroutineDispatcher)

	private val brainJobs = Bag<Job>()

	class AIContext(val entity:Int, val agentS: AgentS) : AbstractCoroutineContextElement(AIContext) {
		companion object Key : CoroutineContext.Key<AIContext>
	}

	fun beginBrain(entity:Int, agentS:AgentS, agentC:AgentC) {
		val brainJob = Job()
		brainJobs[entity] = brainJob
		val brain = agentC.brain
		coroutineScope.launch(brainJob + AIContext(entity, agentS), CoroutineStart.DEFAULT) {
			loop {
				brain()
			}
		}
	}

	fun update(time:Float) {
		val nano = (time * 1000_000_000L).roundToLong()
		dispatcher.update(nano)
	}

	fun endBrain(entity:Int) {
		brainJobs.remove(entity)?.cancel()
	}
}