package com.darkyen.worldSim.ecs

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.LongBinaryHeap
import com.badlogic.gdx.utils.Pool
import com.darkyen.worldSim.SimulationSpeedRegulator
import com.github.antag99.retinazer.Component
import com.github.antag99.retinazer.Mapper
import com.github.antag99.retinazer.Wire
import com.github.antag99.retinazer.systems.FamilyWatcherSystem
import com.github.antag99.retinazer.util.Bag
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Delay
import kotlinx.coroutines.InternalCoroutinesApi
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToLong

typealias Brain = (suspend () -> Nothing)

/**
 *
 */
class IntelligentC(val brain:Brain) : Component

class IntelligentS : FamilyWatcherSystem.Single(COMPONENT_DOMAIN.familyWith(IntelligentC::class.java)) {

	private val dispatcher = AICoroutineDispatcher()

	@Wire
	private lateinit var intelligentC: Mapper<IntelligentC>
	@Wire
	private lateinit var simulationClock : SimulationSpeedRegulator

	@Wire
	private lateinit var world:World
	private val aiWorld:AIWorld by lazy {
		AIWorld(world,
				engine.getMapper(PositionC::class.java),
				engine.getMapper(AgentC::class.java),
				engine.getMapper(AgentSpeechC::class.java),
				dispatcher,
				engine.getService(PathFinder::class.java),
				engine.getService(AgentSpatialLookup::class.java))
	}

	fun continueEntity(entity:Int) {
		dispatcher.continueEntity(entity)
	}

	override fun update() {
		// Process addition and removals
		super.update()

		// Tick brains
		dispatcher.updateAndProcess((simulationClock.simulationDelta * 1000f).roundToLong(), 10_000_000L)
	}

	override fun insertedEntity(entity: Int) {
		val brain = intelligentC[entity]!!.brain
		dispatcher.addEntity(brain, AIContext(entity, aiWorld))
	}

	override fun removedEntity(entity: Int) {
		dispatcher.removeEntity(entity)
	}
}

@UseExperimental(InternalCoroutinesApi::class)
class AICoroutineDispatcher : ContinuationInterceptor, Delay, CoroutineScope {

	override val key: CoroutineContext.Key<*>
		get() = ContinuationInterceptor

	override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> = continuation

	override val coroutineContext: CoroutineContext
		get() = this

	override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
		Gdx.app?.error("Intelligent", "Using of kotlin delay() is deprecated", Exception("--stack trace--"))
		schedule(timeMillis, continuation)
	}

	/** Continuations that are waiting for external update */
	private val waitingContinuation = Bag<Continuation<Unit>>()

	var dispatcherClockMs:Long = 0
		private set

	private val entityNodes = Bag<ContinuationNode>()
	private val heap = LongBinaryHeap<ContinuationNode>(10_000, false)

	/** Schedules a new [continuation] to be woken up after [durationMs]. */
	private fun schedule(durationMs: Long, continuation: Continuation<Unit>):ContinuationNode {
		val heap = heap
		val resumeAt = dispatcherClockMs + durationMs
		val entity = continuation.context[AIContext]!!.entity

		val node: ContinuationNode
		synchronized(heap) {
			node = ContinuationNode.POOL.obtain()!!
			node.continuation = continuation
			heap.add(node, resumeAt)
		}

		entityNodes.set(entity, node)
		return node
	}

	fun addEntity(brain:Brain, context:AIContext) {
		// This creates the brain and runs it up to the first continuation.
		// AICoroutineDispatcher.interceptContinuation could be implemented to stop that from happening, but we don't mind.
		// Then, the delay starts.
		brain.startCoroutine(BrainContinuation(this + context))
	}

	fun removeEntity(entity:Int) {
		waitingContinuation.remove(entity)
		val node = entityNodes.get(entity) ?: return
		node.entityDied = true
	}

	fun continueEntity(entity:Int) {
		val continuation = waitingContinuation.remove(entity)!!
		schedule(0, continuation)
	}

	suspend fun waitEntity(entity:Int) {
		suspendCoroutine<Unit> {
			waitingContinuation.set(entity, it)
		}
	}

	suspend fun delayEntity(durationMs: Long) {
		suspendCoroutine<Unit> {
			schedule(durationMs, it)
		}
	}

	fun updateAndProcess(updateMs:Long, runForMaxNanos:Long) {
		val internalTime = dispatcherClockMs + updateMs
		this.dispatcherClockMs = internalTime
		val realTimeOfForcedEnd = System.nanoTime() + runForMaxNanos

		while (true) {
			for (i in 0 until 100) {
				if (!processOne(internalTime)) {
					return
				}
			}

			val now = System.nanoTime()
			if (now >= realTimeOfForcedEnd) {
				return
			}
		}
	}

	/** Process one scheduled continuation that is ready to be run
	 * (was scheduled to be woken up before or at [currentTime]).
	 * @return true if there may be more entities to process */
	private fun processOne(currentTime:Long):Boolean {
		val heap = heap
		val continuation:Continuation<Unit>
		val dead:Boolean
		synchronized(heap) {
			val topNode = heap.peekOrNull() ?: return false
			if (topNode.value > currentTime) {
				return false
			}
			continuation = topNode.continuation
			dead = topNode.entityDied
			heap.pop()
			ContinuationNode.POOL.free(topNode)
		}

		if (dead) {
			// Do not even call finally, just throw it away
			return true
		}

		try {
			continuation.resume(Unit)
		} catch (e:Exception) {
			Gdx.app.error("Intelligent", "Brain resume failed", e)
		}
		return true
	}

	private class BrainContinuation(override val context:CoroutineContext):Continuation<Nothing> {
		override fun resumeWith(result: Result<Nothing>) {
			if (Gdx.app != null) {
				Gdx.app.error("IntelligentS", "Brain of $context died", result.exceptionOrNull())
			} else {
				System.err.println("Brain of $context died")
				result.exceptionOrNull()?.printStackTrace(System.err)
			}
		}
	}

	class ContinuationNode : LongBinaryHeap.Node(), Pool.Poolable {

		/** The continuation to run */
		var continuation:Continuation<Unit> = NullContinuation
		/** Whether the entity has died while waiting */
		var entityDied = false

		override fun reset() {
			// To allow the previous value to be GCed
			continuation = NullContinuation
			entityDied = false
		}

		object POOL : Pool<ContinuationNode>(10_000) {
			override fun newObject(): ContinuationNode = ContinuationNode()
		}
	}

	private object NullContinuation : Continuation<Unit> {

		override val context: CoroutineContext
			get() = EmptyCoroutineContext

		override fun resumeWith(result: Result<Unit>) {
			throw UnsupportedOperationException("NullContinuation")
		}
	}
}

/** Reference to the world for an entity.
 * Shared between all [AIContext]s. */
class AIWorld(
		@JvmField
		val world:World,
		@JvmField
		val positionMapper:Mapper<PositionC>,
		@JvmField
		val agentMapper:Mapper<AgentC>,
		@JvmField
		val speechMapper:Mapper<AgentSpeechC>,
		@JvmField
		val dispatcher:AICoroutineDispatcher,
		@JvmField
		val pathFinder:PathFinder,
		@JvmField
		val livingAgentSpatialLookup:SpatialLookupService)

class AIContext(
		/** Own entity ID */
		@JvmField
		val entity:Int,
		@JvmField
		private val aiWorld:AIWorld) : AbstractCoroutineContextElement(AIContext) {

	/** Cache for own components */
	@JvmField
	val agent: AgentC = aiWorld.agentMapper[entity]!!
	@JvmField
	val position: PositionC = aiWorld.positionMapper[entity]!!

	// Convenience accessors
	val speech: AgentSpeechC?
		get() = aiWorld.speechMapper[entity]

	val currentTimeMs:Long
		get() = aiWorld.dispatcher.dispatcherClockMs

	val world:World
		get() = aiWorld.world
	val positionMapper:Mapper<PositionC>
		get() = aiWorld.positionMapper
	val agentMapper:Mapper<AgentC>
		get() = aiWorld.agentMapper
	val speechMapper:Mapper<AgentSpeechC>
		get() = aiWorld.speechMapper
	val pathFinder:PathFinder
		get() = aiWorld.pathFinder
	val livingAgentSpatialLookup:SpatialLookupService
		get() = aiWorld.livingAgentSpatialLookup

	/** Do some [activity] until notified externally.
	 * Throws when interrupted. */
	suspend fun wait(activity:AgentActivity) {
		val previousActivity = agent.activity
		agent.activity = activity
		try {
			aiWorld.dispatcher.waitEntity(entity)
		} finally {
			agent.activity = previousActivity
		}
	}

	/** Do some [activity] for given amount of time ([durationMs]).
	 * Throws when interrupted. */
	suspend fun delay(activity:AgentActivity, durationMs:Long) {
		val previousActivity = agent.activity
		agent.activity = activity
		try {
			aiWorld.dispatcher.delayEntity(durationMs)
		} finally {
			agent.activity = previousActivity
		}
	}

	companion object Key : CoroutineContext.Key<AIContext>
}