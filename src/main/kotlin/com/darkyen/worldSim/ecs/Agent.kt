package com.darkyen.worldSim.ecs

import com.darkyen.worldSim.EntityChunkPopulator
import com.darkyen.worldSim.Feature
import com.darkyen.worldSim.ITEMS
import com.darkyen.worldSim.Item
import com.darkyen.worldSim.Tile
import com.darkyen.worldSim.TileType
import com.darkyen.worldSim.ai.AIBrain
import com.darkyen.worldSim.ai.AICoroutineManager
import com.darkyen.worldSim.util.Direction
import com.darkyen.worldSim.util.Vec2
import com.darkyen.worldSim.util.directionTo
import com.darkyen.worldSim.util.forEach
import com.github.antag99.retinazer.Component
import com.github.antag99.retinazer.Mapper
import com.github.antag99.retinazer.Wire
import com.github.antag99.retinazer.systems.FamilyWatcherSystem
import kotlin.coroutines.Continuation
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 *
 */
class AgentC(val brain:AIBrain, val genderMale:Boolean) : Component {
	val inventory = IntArray(ITEMS.size)
	var ageYears:Int = 0

	private var brainTaskContinuation: Continuation<Unit>? = null
	private var waitingFor:Action? = null

	enum class Action {
		MOVEMENT
	}

	fun continueAfter(action:Action) {
		if (waitingFor === action) {
			waitingFor = null
			val continuation = brainTaskContinuation
			brainTaskContinuation = null
			continuation?.resume(Unit)
		}
	}

	suspend fun waitFor(action:Action) {
		assert(waitingFor == null && brainTaskContinuation == null)
		waitingFor = action
		suspendCoroutine<Unit> {
			brainTaskContinuation = it
		}
	}
}

/** When entities reach this year, their sprite changes from child sprite to mature sprite. */
const val MATURITY_AGE_YEAR = 16

class AgentS : FamilyWatcherSystem.Single(COMPONENT_DOMAIN.familyWith(PositionC::class.java, AgentC::class.java)) {

	@Wire
	private lateinit var world: World
	@Wire
	private lateinit var positionC: Mapper<PositionC>
	@Wire
	private lateinit var agentC: Mapper<AgentC>
	@Wire
	private lateinit var renderC: Mapper<RenderC>
	@Wire
	private lateinit var pathFinder:PathFinder

	private val brainSurgeon = AICoroutineManager()

	private var dayProgressSec = 0f
	private val dayLengthSec = 60f * 10f // 10 min = 1 day
	// Days elapsed
	private var yearProgress = 0
	private val yearLengthDays = 50

	override fun update(delta: Float) {
		super.update(delta)
		dayProgressSec += delta
		yearProgress += if (dayProgressSec >= dayLengthSec) {
			dayProgressSec -= dayLengthSec
			1
		} else 0
		if (yearProgress >= yearLengthDays) {
			yearProgress -= yearLengthDays
			advanceYear()
		}

		brainSurgeon.update(delta)
	}

	private fun advanceYear() {
		// Age everyone by 1 year
		entities.indices.forEach { entity ->
			val agent = agentC[entity]
			agent.ageYears += 1
			if (agent.ageYears == MATURITY_AGE_YEAR) {
				// Update sprite from child sprite
				renderC[entity]?.let {
					it.sprite = (if (agent.genderMale) EntityChunkPopulator.maleSprites else EntityChunkPopulator.femaleSprites).random()
				}
			}
		}
	}

	override fun insertedEntity(entity: Int, delta: Float) {
		brainSurgeon.beginBrain(entity, this, agentC[entity]!!)
	}

	override fun removedEntity(entity: Int, delta: Float) {
		brainSurgeon.endBrain(entity)
	}

	companion object {

		/** Walk single tile in the given direction.
		 * @return whether successful */
		suspend fun walk(direction: Direction):Boolean {
			val aiContext = coroutineContext[AICoroutineManager.AIContext]!!
			val entity = aiContext.entity
			val agentS = aiContext.agentS
			val agentC = agentS.agentC[entity]
			val positionC = agentS.positionC[entity]
			val moveFrom = positionC.pos
			val moveTo = moveFrom + direction.vec

			if (agentS.world.getTile(moveTo).type != TileType.LAND) {
				return false
			}

			positionC.movement = direction
			positionC.speed = agentS.world.getMovementSpeedMultiplier(moveFrom)
			agentC.waitFor(AgentC.Action.MOVEMENT)
			return true
		}

		suspend fun walkTo(targetPosition: Vec2):Boolean {
			val aiContext = coroutineContext[AICoroutineManager.AIContext]!!
			val entity = aiContext.entity
			val agentS = aiContext.agentS
			val positionC = agentS.positionC[entity]
			var currentPos = positionC.pos
			val path = agentS.pathFinder.findPath(currentPos, targetPosition) ?: return false

			for (i in 0 until path.length) {
				val nextPos = path.node(i)
				val dir = currentPos.directionTo(nextPos)
				if (!walk(dir)) {
					return false
				}
				currentPos = nextPos
			}

			return true
		}

		/** Own position */
		suspend fun position(): Vec2 {
			val aiContext = coroutineContext[AICoroutineManager.AIContext]!!
			return aiContext.agentS.positionC[aiContext.entity].pos
		}

		/** How many items of this type are in my inventory? */
		suspend fun inventoryCount(item: Item):Int {
			val aiContext = coroutineContext[AICoroutineManager.AIContext]!!
			return aiContext.agentS.agentC[aiContext.entity].inventory[item.ordinal]
		}

		/** How many items of this type are on this tile? */
		suspend fun tileCount(item: Item):Int {
			val aiContext = coroutineContext[AICoroutineManager.AIContext]!!
			val pos = aiContext.agentS.positionC[aiContext.entity].pos
			return aiContext.agentS.world.getItemCount(pos, item)
		}

		/** Get tile at [position] + [offset].
		 * Can't look further than [MAX_LOOK_DISTANCE] manhattan distance. */
		suspend fun tileAt(offset:Vec2): Tile {
			assert (offset.manhLen <= MAX_LOOK_DISTANCE)
			val aiContext = coroutineContext[AICoroutineManager.AIContext]!!
			val pos = aiContext.agentS.positionC[aiContext.entity].pos
			return aiContext.agentS.world.getTile(pos + offset)
		}

		/** Get feature at [position] + [offset].
		 * Can't look further than [MAX_LOOK_DISTANCE] manhattan distance. */
		suspend fun featureAt(offset:Vec2): Feature? {
			assert (offset.manhLen <= MAX_LOOK_DISTANCE)
			val aiContext = coroutineContext[AICoroutineManager.AIContext]!!
			val pos = aiContext.agentS.positionC[aiContext.entity].pos
			return aiContext.agentS.world.getFeature(pos + offset)
		}

		const val MAX_LOOK_DISTANCE = 4
	}

}

