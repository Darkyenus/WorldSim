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
import com.darkyen.worldSim.ecs.MEMORY_CAPACITY
import com.darkyen.worldSim.ecs.MEMORY_TYPES
import com.darkyen.worldSim.ecs.MemoryType
import com.darkyen.worldSim.ecs.PositionC
import com.darkyen.worldSim.ecs.get
import com.darkyen.worldSim.ecs.say
import com.darkyen.worldSim.ecs.set
import com.darkyen.worldSim.util.Direction
import com.darkyen.worldSim.util.GdxIntArray
import com.darkyen.worldSim.util.Vec2
import com.darkyen.worldSim.util.anyPositionNearIs
import com.darkyen.worldSim.util.directionTo
import com.darkyen.worldSim.util.indexOfMax
import kotlinx.coroutines.delay
import java.util.*
import kotlin.math.max
import kotlin.random.Random

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

val AIContext.currentTimeMs:Long
	get() = agentS.currentTimeMs

/** Do some [activity] for given amount of time ([durationMs]).
 * Throws when interrupted. */
suspend fun AIContext.doActivityDelay(activity:AgentActivity, durationMs:Long) {
	val previousActivity = agent.activity
	agent.activity = activity
	try {
		delay(durationMs)
	} finally {
		agent.activity = previousActivity
	}
}


/** Walk single tile in the given direction.
 * @return whether successful */
suspend fun AIContext.walk(direction: Direction, activity:AgentActivity = AgentActivity.WALKING):Boolean {
	val entity = entity
	val agentS = agentS
	val agentC = agent
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

/** Memorize something. May remove some other memory if the memory is full. */
fun AIContext.memorize(type: MemoryType, newLocation:Vec2) {
	val memoryTypes = agent.positionMemoryType
	val memoryLocations = agent.positionMemoryLocation
	val typeOrdinal = type.ordinal.toByte()

	val firstEmptyMemoryIndex = memoryTypes.indexOf(0)
	if (firstEmptyMemoryIndex != -1) {
		// Good, memorize it there
		memoryTypes[firstEmptyMemoryIndex] = typeOrdinal
		memoryLocations[firstEmptyMemoryIndex] = newLocation.packed
		return
	}

	val myPos = this.position.pos

	val memoryTypeCounts = ByteArray(memoryTypes.size)

	// Count all memory types to find what is the least useful memory
	// If we encounter that we already remember something similar to this, update it and quit
	for (i in memoryTypes.indices) {
		val memoryType = memoryTypes[i]
		memoryTypeCounts[memoryType.toInt()]++
		if (memoryType != typeOrdinal) {
			continue
		}
		val loc = Vec2(memoryLocations[i])
		if (loc.manhDst(newLocation) < 10) {
			// This is really close to a previous memory, update if closer to us and be done with it
			if (myPos.manhDst(newLocation) < loc.manhDst(newLocation)) {
				memoryLocations[i] = newLocation.packed
			}
			return
		}
	}

	val mostAbundantMemory = memoryTypeCounts.indexOfMax()
	// Pick random abundant memory and forget it, replacing it with our new info
	var memoryToForget = Random.nextInt(memoryTypeCounts[mostAbundantMemory].toInt())
	for (i in memoryTypes.indices) {
		val memoryType = memoryTypes[i]
		if (memoryType.toInt() != mostAbundantMemory) {
			continue
		}
		if (memoryToForget-- == 0) {
			// Forget this one
			memoryTypes[i] = typeOrdinal
			memoryLocations[i] = newLocation.packed
			return
		}
	}

	assert(false) { "Should have forgotten something!" }
}

/** Recall from memory the location of the nearest location of given type.
 * May return [Vec2.NULL] if no such location exists. */
fun AIContext.recallNearest(type: MemoryType):Vec2 {
	val memoryTypes = agent.positionMemoryType
	val memoryLocations = agent.positionMemoryLocation
	val typeOrdinal = type.ordinal.toByte()
	val myPos = this.position.pos

	var nearest = Vec2.NULL
	var nearestDst = Integer.MAX_VALUE

	for (i in memoryTypes.indices) {
		val memoryType = memoryTypes[i]
		if (memoryType != typeOrdinal) {
			continue
		}

		val memLoc = Vec2(memoryLocations[i])
		val dst = memLoc.manhDst(myPos)
		if (dst < nearestDst) {
			nearest = memLoc
			nearestDst = dst
		}
	}

	return nearest
}

const val AGENT_VISIBILITY_DISTANCE = 10

private inline fun AIContext.forNearbyEntities(action:(mDistance:Int, entity:Int) -> Unit) {
	val self = entity
	val pos = position.pos
	agentS.agentSpatialLookup.forEntitiesNear(pos, AGENT_VISIBILITY_DISTANCE) { entity, distance ->
		if (entity != self) {
			action(distance, entity)
		}
	}
}

/** Find the nearest person (if there is any close enough) */
fun AIContext.findNearbyAgents():GdxIntArray {
	val entities = GdxIntArray(true, 16)
	val distances = GdxIntArray(true, 16)

	forNearbyEntities { mDistance, entity ->
		var i = Arrays.binarySearch(distances.items, 0, distances.size, mDistance)
		if (i < 0) {
			i = -(i+1)
		}

		distances.insert(i, mDistance)
		entities.insert(i, entity)
	}

	return entities
}

suspend fun AIContext.eatFromInventory():Boolean {
	val foodAmount = agent.inventory[Item.FOOD.ordinal]
	if (foodAmount > 0) {
		// Eat
		doActivityDelay(AgentActivity.EATING, FOOD_EAT_TIME_MS)
		agent.inventory[Item.FOOD.ordinal] -= 1
		agent.attributes[AgentAttribute.HUNGER] = agent.attributes[AgentAttribute.HUNGER] + HUNGER_POINTS_PER_FOOD
		return true
	}
	return false
}

suspend fun AIContext.drinkFromInventory():Boolean {
	val canteenAmount = agent.inventory[Item.WATER_CANTEEN_FULL.ordinal]
	if (canteenAmount > 0) {
		doActivityDelay(AgentActivity.DRINKING_FROM_CANTEEN, DRINK_DURATION_MS)
		agent.inventory[Item.WATER_CANTEEN_FULL.ordinal] -= 1
		agent.inventory[Item.WATER_CANTEEN_EMPTY.ordinal] += 1
		agent.attributes[AgentAttribute.THIRST] = agent.attributes[AgentAttribute.THIRST] + THIRST_POINTS_PER_DRINK_CONTAINER
		return true
	}
	return false
}

suspend fun AIContext.drinkFromEnvironment():Boolean {
	val world = agentS.world
	val myPos = position.pos
	val waterNearby = anyPositionNearIs(myPos) { pos -> world.getTile(pos).type == TileType.WATER }

	if (!waterNearby) {
		return false
	}

	memorize(MemoryType.WATER_SOURCE_POSITION, myPos)

	doActivityDelay(AgentActivity.DRINKING, ENVIRONMENT_DRINK_DURATION_MS)
	agent.attributes[AgentAttribute.THIRST] = AgentAttribute.THIRST.max
	return true
}

suspend fun AIContext.refillCanteen():Boolean {
	val world = agentS.world
	val myPos = position.pos
	val waterNearby = anyPositionNearIs(myPos) { pos -> world.getTile(pos).type == TileType.WATER }
	if (!waterNearby) {
		return false
	}

	memorize(MemoryType.WATER_SOURCE_POSITION, myPos)

	val inventory = agent.inventory
	if (inventory[Item.WATER_CANTEEN_EMPTY.ordinal] <= 0) {
		return false
	}

	// Refill
	doActivityDelay(AgentActivity.REFILLING_CANTEEN, REFILL_CANTEEN_DURATION_MS)
	inventory[Item.WATER_CANTEEN_EMPTY.ordinal]--
	inventory[Item.WATER_CANTEEN_FULL.ordinal]++
	return true
}

suspend fun AIContext.gatherCraftingResourcesFromEnvironment():Boolean {
	val myPos = position.pos
	val tileFeatures = agentS.world.getFeature(myPos) ?: return false
	val aspects = tileFeatures.aspects
	if (FeatureAspect.CRAFTING_MATERIAL_SOURCE in aspects) {
		memorize(MemoryType.CRATING_MATERIAL_SOURCE_POSITION, myPos)

		doActivityDelay(AgentActivity.GATHERING_CRAFTING_MATERIAL, GATHERING_CRAFTING_MATERIAL_DURATION_MS)
		agent.inventory[Item.CRAFTING_MATERIAL.ordinal]++
		return true
	}
	return false
}

suspend fun AIContext.gatherWoodFromEnvironment():Boolean {
	val myPos = position.pos
	val tileFeatures = agentS.world.getFeature(myPos) ?: return false
	val aspects = tileFeatures.aspects
	if (FeatureAspect.WOOD_SOURCE in aspects) {
		memorize(MemoryType.WOOD_SOURCE_POSITION, myPos)

		doActivityDelay(AgentActivity.GATHERING_WOOD, GATHERING_WOOD_DURATION_MS)
		agent.inventory[Item.WOOD.ordinal]++
		return true
	}
	return false
}

suspend fun AIContext.gatherStoneFromEnvironment():Boolean {
	val myPos = position.pos
	val tileFeatures = agentS.world.getFeature(myPos) ?: return false
	val aspects = tileFeatures.aspects
	if (FeatureAspect.STONE_SOURCE in aspects) {
		memorize(MemoryType.STONE_SOURCE_POSITION, myPos)

		doActivityDelay(AgentActivity.GATHERING_STONE, GATHERING_STONE_DURATION_MS)
		agent.inventory[Item.STONE.ordinal]++
	}
	return false
}

suspend fun AIContext.gatherFoodFromEnvironment():Boolean {
	val myPos = position.pos
	val world = agentS.world
	val tileFeatures = world.getFeature(myPos) ?: return false

	val aspects = tileFeatures.aspects
	if (FeatureAspect.FOOD_SOURCE_FRUIT in aspects) {
		memorize(MemoryType.FOOD_SOURCE_POSITION, myPos)

		doActivityDelay(AgentActivity.GATHERING_FRUIT, GATHERING_DURATION_MS)
		agent.inventory[Item.FOOD.ordinal]++
		return true
	}

	if (FeatureAspect.FOOD_SOURCE_MUSHROOMS in aspects) {
		memorize(MemoryType.FOOD_SOURCE_POSITION, myPos)

		doActivityDelay(AgentActivity.GATHERING_MUSHROOMS, GATHERING_DURATION_MS)
		agent.inventory[Item.FOOD.ordinal]++
		return true
	}

	if (!agent.isBaby && FeatureAspect.FOOD_SOURCE_WILD_ANIMALS in aspects) {
		memorize(MemoryType.FOOD_SOURCE_POSITION, myPos)
		// TODO(jp): Skill check
		doActivityDelay(AgentActivity.HUNTING, HUNTING_DURATION_MS)
		agent.inventory[Item.FOOD.ordinal] += 7
		return true
	}

	if (FeatureAspect.FOOD_SOURCE_SMALL_WILD_ANIMALS in aspects) {
		memorize(MemoryType.FOOD_SOURCE_POSITION, myPos)
		// TODO(jp): Skill check
		doActivityDelay(AgentActivity.HUNTING, HUNTING_SMALL_DURATION_MS)
		agent.inventory[Item.FOOD.ordinal] += 5
		return true
	}

	return false
}

suspend fun AIContext.sleep() {
	val sleepForPoints = max(AgentAttribute.SLEEP.max - agent.attributes[AgentAttribute.SLEEP], 10)
	for (i in 0 until sleepForPoints) {
		doActivityDelay(AgentActivity.SLEEPING, SLEEP_DURATION_MS_PER_POINT)
		agent.attributes[AgentAttribute.SLEEP] = agent.attributes[AgentAttribute.SLEEP] + 1
	}
}

suspend fun AIContext.talkWith(entity:Int):Boolean {
	// TODO somehow engage the other entity into this?
	val otherAgentC = agentS.agentC[entity] ?: return false
	if (!otherAgentC.activity.canListen) {
		return false
	}
	val otherPositionC = agentS.positionC[entity] ?: return false
	if ((position.pos - otherPositionC.pos).manhLen != 1) {
		return false
	}

	// All checks are ok, lets talk
	for (i in 0 until 5) {
		val typeToSay:MemoryType
		val infoToSay:Vec2
		// Sometimes say something useful
		when (Random.nextBits(3)) {
			0 -> {
				// Tell something or ask about something
				val index = Random.nextInt(MEMORY_CAPACITY)
				val chosenInformationType = MEMORY_TYPES[agent.positionMemoryType[index].toInt()]
				if (chosenInformationType == MemoryType.NO_MEMORY) {
					// Ask instead
					typeToSay = MEMORY_TYPES.random()
					infoToSay = Vec2.NULL
				} else {
					typeToSay = chosenInformationType
					infoToSay = Vec2(agent.positionMemoryLocation[index])
				}
			}
			1 -> {
				// Ask about something
				typeToSay = MEMORY_TYPES.random()
				infoToSay = Vec2.NULL
			}
			else -> {
				// Just chatter
				typeToSay = MemoryType.NO_MEMORY
				infoToSay = Vec2.NULL
			}
		}

		if (typeToSay == MemoryType.NO_MEMORY) {
			// Just chat
			doActivityDelay(AgentActivity.CHATTING, TALK_DURATION_MS_PER_POINT)
			val agent = agent
			val attributes = agent.attributes
			attributes[AgentAttribute.SOCIAL] = attributes[AgentAttribute.SOCIAL] + 1
		} else {
			// Ask or Tell
			say(typeToSay, infoToSay, TALK_DURATION_MS_PER_POINT, socialReward=1)
		}
	}

	return true
}

suspend fun AIContext.craftItem(item:Item):Boolean {
	val craftMaterialRequirement = item.craftMaterialRequirement
	if (craftMaterialRequirement < 0) {
		return false
	}

	if (agent.inventory[Item.CRAFTING_MATERIAL.ordinal] < craftMaterialRequirement) {
		return false
	}

	val duration = craftMaterialRequirement * CRAFTING_DURATION_MS_PER_MATERIAL

	doActivityDelay(AgentActivity.CRAFTING, duration)
	agent.inventory[Item.CRAFTING_MATERIAL.ordinal] -= craftMaterialRequirement
	agent.inventory[item.ordinal]++
	return true
}

const val MAX_LOOK_DISTANCE = 8

const val HUNGER_POINTS_PER_FOOD = 50
const val THIRST_POINTS_PER_DRINK_CONTAINER = 50

const val FOOD_EAT_TIME_MS = 5000L
const val DRINK_DURATION_MS = 3000L
const val ENVIRONMENT_DRINK_DURATION_MS = 5000L
const val REFILL_CANTEEN_DURATION_MS = 2000L
const val SLEEP_DURATION_MS_PER_POINT = 1800L
const val TALK_DURATION_MS_PER_POINT = 1000L

const val GATHERING_DURATION_MS = 5000L
const val HUNTING_DURATION_MS = 20_000L
const val HUNTING_SMALL_DURATION_MS = 15_000L
const val GATHERING_CRAFTING_MATERIAL_DURATION_MS = 10_000L
const val GATHERING_WOOD_DURATION_MS = 20_000L
const val GATHERING_STONE_DURATION_MS = 40_000L
const val CRAFTING_DURATION_MS_PER_MATERIAL = 2_000L
