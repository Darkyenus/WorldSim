package com.darkyen.worldSim.ai

import com.badlogic.gdx.math.MathUtils
import com.darkyen.worldSim.Feature
import com.darkyen.worldSim.FeatureAspect
import com.darkyen.worldSim.Item
import com.darkyen.worldSim.Tile
import com.darkyen.worldSim.TileType
import com.darkyen.worldSim.ecs.AgentActivity
import com.darkyen.worldSim.ecs.AgentAttribute
import com.darkyen.worldSim.ecs.AgentC
import com.darkyen.worldSim.ecs.AgentS
import com.darkyen.worldSim.ecs.PositionC
import com.darkyen.worldSim.ecs.get
import com.darkyen.worldSim.ecs.set
import com.darkyen.worldSim.util.Direction
import com.darkyen.worldSim.util.Vec2
import com.darkyen.worldSim.util.anyPositionNearIs
import com.darkyen.worldSim.util.directionTo
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** Function that is called forever and does stuff. */
typealias AIBrain = (suspend AIContext.() -> Unit)
/** Part of AI programming that can succeed or fail. */
typealias AITask = (suspend AIContext.() -> Boolean)

class AIContext(val entity:Int, val agentS: AgentS, val agent: AgentC, val position: PositionC)

inline fun loop(action:()->Unit):Nothing {
	while (true) {
		action()
	}
}

private object GuardFailed : Throwable("Guard failed", null, false, false)

suspend fun guard(guard:() -> Boolean, activity:suspend ()->Unit):Boolean {
	try {
		withContext(object : ContinuationInterceptor {
			override val key = ContinuationInterceptor

			override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
				return object : Continuation<T> {
					override val context: CoroutineContext
						get() = continuation.context

					override fun resumeWith(result: Result<T>) {
						if (result.isFailure) {
							continuation.resumeWith(result)
						} else {
							val check = guard()
							println("Checking conditions, looking $check")
							if (check) {
								continuation.resumeWith(result)
							} else {
								continuation.resumeWithException(GuardFailed)
							}
						}
					}
				}
			}
		}) {
			activity()
		}
		return true
	} catch (e :GuardFailed) {
		return false
	}
}

val AIContext.currentTimeMs:Long
	get() = agentS.currentTimeMs

suspend inline fun AIContext.doActivityDelay(activity:AgentActivity, durationMs:Long, onComplete:(actualDurationMs:Long) -> Unit) {
	val previousActivity = agent.activity
	agent.activity = activity
	val startTime = currentTimeMs
	try {
		delay(durationMs)
	} finally {
		agent.activity = previousActivity
		onComplete(currentTimeMs - startTime)
	}
}


/** Walk single tile in the given direction.
 * @return whether successful */
suspend fun AIContext.walk(direction: Direction, activity:AgentActivity = AgentActivity.WALKING):Boolean {
	val entity = entity
	val agentS = agentS
	val agentC = agentS.agentC[entity]
	val positionC = agentS.positionC[entity]
	val moveFrom = positionC.pos
	val moveTo = moveFrom + direction.vec

	if (agentS.world.getTile(moveTo).type != TileType.LAND) {
		return false
	}

	positionC.movement = direction
	positionC.speed = agentS.world.getMovementSpeedMultiplier(moveFrom) * MathUtils.lerp(0.6f, 1.4f, agentC.attributePercent(AgentAttribute.AGILITY))
	agentS.waitEntity(entity, activity)
	return true
}

suspend fun AIContext.walkTo(targetPosition: Vec2, onlyNear:Boolean = false, activity:AgentActivity = AgentActivity.WALKING):Boolean {
	val entity = entity
	val agentS = agentS
	val positionC = agentS.positionC[entity]
	var currentPos = positionC.pos
	val path = if (onlyNear) {
		agentS.pathFinder.findPathNear(currentPos, targetPosition)
	} else {
		agentS.pathFinder.findPath(currentPos, targetPosition)
	} ?: return false

	for (i in 0 until path.length) {
		val nextPos = path.node(i)
		val dir = currentPos.directionTo(nextPos)
		if (!walk(dir, activity)) {
			return false
		}
		currentPos = nextPos
	}

	return true
}

/** Own position */
fun AIContext.position(): Vec2 {
	return position.pos
}

/** Own attributes. Do not change! */
fun AIContext.attributes(): ByteArray {
	return agent.attributes
}

fun AIContext.increaseAlert(amount:Byte) {
	val alertness = agent.attributes[AgentAttribute.ALERTNESS]
	if (alertness < amount) {
		agent.attributes[AgentAttribute.ALERTNESS] = amount
	} else if (alertness < amount * 2) {
		agent.attributes[AgentAttribute.ALERTNESS] = alertness + (amount * 2 - alertness) / 2
	}
}

/** How many items of this type are in my inventory? */
fun AIContext.inventoryCount(item: Item):Int {
	return agent.inventory[item.ordinal]
}

/** How many items of this type are on this tile? */
fun AIContext.tileCount(item: Item):Int {
	return agentS.world.getItemCount(position.pos, item)
}

/** Get tile at [position] + [offset].
 * Can't look further than [MAX_LOOK_DISTANCE] manhattan distance. */
fun AIContext.tileAt(offset: Vec2): Tile {
	assert (offset.manhLen <= MAX_LOOK_DISTANCE)
	return agentS.world.getTile(position.pos + offset)
}

/** Get feature at [position] + [offset].
 * Can't look further than [MAX_LOOK_DISTANCE] manhattan distance. */
fun AIContext.featureAt(offset: Vec2): Feature? {
	return agentS.world.getFeature(position.pos + offset)
}

suspend fun AIContext.eatFromInventory():Boolean {
	val foodAmount = agent.inventory[Item.FOOD.ordinal]
	if (foodAmount > 0) {
		agent.inventory[Item.FOOD.ordinal] -= 1
		// Eat
		agent.attributes[AgentAttribute.HUNGER] = agent.attributes[AgentAttribute.HUNGER] + HUNGER_POINTS_PER_FOOD
		doActivityDelay(AgentActivity.EATING, FOOD_EAT_TIME_MS) {}
		return true
	}
	return false
}

suspend fun AIContext.drinkFromInventory():Boolean {
	val foodAmount = agent.inventory[Item.WATER_CONTAINER_FULL.ordinal]
	if (foodAmount > 0) {
		agent.inventory[Item.WATER_CONTAINER_FULL.ordinal] -= 1
		agent.inventory[Item.WATER_CONTAINER_EMPTY.ordinal] += 1
		// Eat
		agent.attributes[AgentAttribute.THIRST] = agent.attributes[AgentAttribute.THIRST] + THIRST_POINTS_PER_DRINK_CONTAINER

		doActivityDelay(AgentActivity.DRINKING, DRINK_DURATION_MS) {}
		return true
	}
	return false
}

suspend fun AIContext.drinkFromEnvironment():Boolean {
	val world = agentS.world
	val waterNearby = anyPositionNearIs(position.pos) { pos -> world.getTile(pos).type == TileType.WATER }

	if (!waterNearby) {
		return false
	}

	// Drink
	agent.attributes[AgentAttribute.THIRST] = AgentAttribute.THIRST.max
	doActivityDelay(AgentActivity.DRINKING, ENVIRONMENT_DRINK_DURATION_MS) {}
	return true
}

suspend fun AIContext.gatherFoodFromEnvironment():Boolean {
	val pos = position.pos
	val world = agentS.world
	val tileFeatures = world.getFeature(pos) ?: return false

	val aspects = tileFeatures.aspects
	if (FeatureAspect.FOOD_SOURCE_FRUIT in aspects) {
		doActivityDelay(AgentActivity.GATHERING_FRUIT, GATHERING_DURATION_MS) {
			if (it >= GATHERING_DURATION_MS) {
				agent.inventory[Item.FOOD.ordinal]++
			} else println("interrupted gathering") //TODO
		}
		return true
	}

	if (FeatureAspect.FOOD_SOURCE_MUSHROOMS in aspects) {
		doActivityDelay(AgentActivity.GATHERING_MUSHROOMS, GATHERING_DURATION_MS) {
			if (it >= GATHERING_DURATION_MS) {
				agent.inventory[Item.FOOD.ordinal]++
			} else println("interrupted gathering") //TODO
		}
		return true
	}

	if (FeatureAspect.FOOD_SOURCE_WILD_ANIMALS in aspects) {
		// TODO(jp): Skill check
		doActivityDelay(AgentActivity.HUNTING, HUNTING_DURATION_MS) {
			if (it >= HUNTING_DURATION_MS) {
				agent.inventory[Item.FOOD.ordinal] += 7
			} else println("interrupted hunting") //TODO
		}
		return true
	}

	if (FeatureAspect.FOOD_SOURCE_SMALL_WILD_ANIMALS in aspects) {
		// TODO(jp): Skill check
		doActivityDelay(AgentActivity.HUNTING, HUNTING_SMALL_DURATION_MS) {
			if (it >= HUNTING_SMALL_DURATION_MS) {
				agent.inventory[Item.FOOD.ordinal] += 5
			} else println("interrupted hunting") //TODO
		}
		return true
	}

	return false
}

suspend fun AIContext.sleep() {
	val sleepFor = max(AgentAttribute.SLEEP.max - agent.attributes[AgentAttribute.SLEEP], 10)
	val duration = sleepFor * SLEEP_DURATION_MS_PER_POINT

	doActivityDelay(AgentActivity.SLEEPING, duration) { realDuration ->
		val sleepPoints = ((realDuration + (SLEEP_DURATION_MS_PER_POINT/2)) / SLEEP_DURATION_MS_PER_POINT).toInt()
		agent.attributes[AgentAttribute.SLEEP] = agent.attributes[AgentAttribute.SLEEP] + sleepPoints
	}
}

const val MAX_LOOK_DISTANCE = 4

const val HUNGER_POINTS_PER_FOOD = 50
const val THIRST_POINTS_PER_DRINK_CONTAINER = 50

const val FOOD_EAT_TIME_MS = 2000L
const val DRINK_DURATION_MS = 1000L
const val ENVIRONMENT_DRINK_DURATION_MS = 2000L
const val SLEEP_DURATION_MS_PER_POINT = 1800L

const val GATHERING_DURATION_MS = 5L
const val HUNTING_DURATION_MS = 20L
const val HUNTING_SMALL_DURATION_MS = 15L