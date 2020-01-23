package com.darkyen.worldSim.ai

import com.github.antag99.retinazer.util.Bag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * Runs braainss.
 */
class AICoroutineManager {

	private val dispatcher = AICoroutineExecutorService()
	private val coroutineDispatcher = dispatcher.asCoroutineDispatcher()
	private val coroutineScope = CoroutineScope(coroutineDispatcher)

	private val brainJobs = Bag<Job>()

	fun beginBrain(context:AIContext) {
		val brainJob = Job()
		brainJobs[context.entity] = brainJob
		val brain = context.agent.brain
		coroutineScope.launch(brainJob, CoroutineStart.DEFAULT) {
			with(context) {
				loop {
					brain()
				}
			}
		}
	}

	val currentNanoTime:Long
		get() = dispatcher.currentNanoTime

	fun update(time:Float) {
		val nano = (time * 1000_000_000L).roundToLong()
		dispatcher.update(nano)
	}

	fun endBrain(entity:Int) {
		brainJobs.remove(entity)?.cancel()
	}
}