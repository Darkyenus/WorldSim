package com.darkyen.worldSim.ai

import com.badlogic.gdx.math.MathUtils
import com.darkyen.worldSim.Feature
import com.darkyen.worldSim.FeatureAspect
import com.darkyen.worldSim.Item
import com.darkyen.worldSim.Tile
import com.darkyen.worldSim.TileType
import com.darkyen.worldSim.ecs.AIContext
import com.darkyen.worldSim.ecs.AgentActivity
import com.darkyen.worldSim.ecs.AgentAttribute
import com.darkyen.worldSim.ecs.MEMORY_CAPACITY
import com.darkyen.worldSim.ecs.MEMORY_TYPES
import com.darkyen.worldSim.ecs.MemoryType
import com.darkyen.worldSim.ecs.get
import com.darkyen.worldSim.ecs.say
import com.darkyen.worldSim.ecs.set
import com.darkyen.worldSim.util.Direction
import com.darkyen.worldSim.util.GdxIntArray
import com.darkyen.worldSim.util.Vec2
import com.darkyen.worldSim.util.anyPositionNearIs
import com.darkyen.worldSim.util.directionTo
import com.darkyen.worldSim.util.indexOfMax
import java.util.*
import kotlin.math.max
import kotlin.random.Random

/** Part of AI programming that can succeed or fail. */
typealias AITask = (suspend AIContext.() -> Boolean)

inline fun loop(action:()->Unit):Nothing {
	while (true) {
		action()
	}
}

/** Walk single tile in the given direction.
 * @return whether successful */
suspend fun AIContext.walk(direction: Direction, activity:AgentActivity = AgentActivity.WALKING):Boolean {
	val agentC = agent
	val positionC = position
	val moveFrom = positionC.pos
	val moveTo = moveFrom + direction.vec

	if (world.getTile(moveTo).type != TileType.LAND) {
		return false
	}

	positionC.movement = direction
	positionC.speed = world.getMovementSpeedMultiplier(moveFrom) * MathUtils.lerp(0.6f, 1.4f, agentC.attributePercent(AgentAttribute.AGILITY))
	wait(activity)
	return true
}

suspend fun AIContext.walkTo(targetPosition: Vec2, onlyNear:Boolean = false, activity:AgentActivity = AgentActivity.WALKING):Boolean {
	val positionC = position
	var currentPos = positionC.pos
	val path = if (onlyNear) {
		pathFinder.findPathNear(currentPos, targetPosition)
	} else {
		pathFinder.findPath(currentPos, targetPosition)
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
	return world.getItemCount(position.pos, item)
}

/** Get tile at [position].
 * Can't look further than [MAX_LOOK_DISTANCE] manhattan distance. */
fun AIContext.tileAt(position: Vec2): Tile {
	assert (this.position.pos.manhDst(position) <= MAX_LOOK_DISTANCE) { (this.position.pos - position).toString() }
	return world.getTile(position)
}

/** Get feature at [position].
 * Can't look further than [MAX_LOOK_DISTANCE] manhattan distance. */
fun AIContext.featureAt(position: Vec2): Feature? {
	assert (this.position.pos.manhDst(position) <= MAX_LOOK_DISTANCE) { (this.position.pos - position).toString() }
	return world.getFeature(position)
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
	livingAgentSpatialLookup.forEntitiesNear(pos, AGENT_VISIBILITY_DISTANCE) { entity, distance ->
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
		delay(AgentActivity.EATING, FOOD_EAT_TIME_MS)
		agent.inventory[Item.FOOD.ordinal] -= 1
		agent.attributes[AgentAttribute.HUNGER] = agent.attributes[AgentAttribute.HUNGER] + HUNGER_POINTS_PER_FOOD
		return true
	}
	return false
}

suspend fun AIContext.drinkFromInventory():Boolean {
	val canteenAmount = agent.inventory[Item.WATER_CANTEEN_FULL.ordinal]
	if (canteenAmount > 0) {
		delay(AgentActivity.DRINKING_FROM_CANTEEN, DRINK_DURATION_MS)
		agent.inventory[Item.WATER_CANTEEN_FULL.ordinal] -= 1
		agent.inventory[Item.WATER_CANTEEN_EMPTY.ordinal] += 1
		agent.attributes[AgentAttribute.THIRST] = agent.attributes[AgentAttribute.THIRST] + THIRST_POINTS_PER_DRINK_CONTAINER
		return true
	}
	return false
}

suspend fun AIContext.drinkFromEnvironment():Boolean {
	val world = world
	val myPos = position.pos
	val waterNearby = anyPositionNearIs(myPos) { pos -> world.getTile(pos).type == TileType.WATER }

	if (!waterNearby) {
		return false
	}

	memorize(MemoryType.WATER_SOURCE_POSITION, myPos)

	delay(AgentActivity.DRINKING, ENVIRONMENT_DRINK_DURATION_MS)
	agent.attributes[AgentAttribute.THIRST] = AgentAttribute.THIRST.max
	return true
}

suspend fun AIContext.refillCanteen():Boolean {
	val world = world
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
	delay(AgentActivity.REFILLING_CANTEEN, REFILL_CANTEEN_DURATION_MS)
	inventory[Item.WATER_CANTEEN_EMPTY.ordinal]--
	inventory[Item.WATER_CANTEEN_FULL.ordinal]++
	return true
}

suspend fun AIContext.gatherCraftingResourcesFromEnvironment():Boolean {
	val myPos = position.pos
	val tileFeatures = world.getFeature(myPos) ?: return false
	val aspects = tileFeatures.aspects
	if (FeatureAspect.CRAFTING_MATERIAL_SOURCE in aspects) {
		memorize(MemoryType.CRATING_MATERIAL_SOURCE_POSITION, myPos)

		delay(AgentActivity.GATHERING_CRAFTING_MATERIAL, GATHERING_CRAFTING_MATERIAL_DURATION_MS)
		agent.inventory[Item.CRAFTING_MATERIAL.ordinal]++
		return true
	}
	return false
}

suspend fun AIContext.gatherWoodFromEnvironment():Boolean {
	val myPos = position.pos
	val tileFeatures = world.getFeature(myPos) ?: return false
	val aspects = tileFeatures.aspects
	if (FeatureAspect.WOOD_SOURCE in aspects) {
		memorize(MemoryType.WOOD_SOURCE_POSITION, myPos)

		delay(AgentActivity.GATHERING_WOOD, GATHERING_WOOD_DURATION_MS)
		agent.inventory[Item.WOOD.ordinal]++
		return true
	}
	return false
}

suspend fun AIContext.gatherStoneFromEnvironment():Boolean {
	val myPos = position.pos
	val tileFeatures = world.getFeature(myPos) ?: return false
	val aspects = tileFeatures.aspects
	if (FeatureAspect.STONE_SOURCE in aspects) {
		memorize(MemoryType.STONE_SOURCE_POSITION, myPos)

		delay(AgentActivity.GATHERING_STONE, GATHERING_STONE_DURATION_MS)
		agent.inventory[Item.STONE.ordinal]++
	}
	return false
}

suspend fun AIContext.gatherFoodFromEnvironment():Boolean {
	val myPos = position.pos
	val tileFeatures = world.getFeature(myPos) ?: return false

	val aspects = tileFeatures.aspects
	if (FeatureAspect.FOOD_SOURCE_FRUIT in aspects) {
		memorize(MemoryType.FOOD_SOURCE_POSITION, myPos)

		delay(AgentActivity.GATHERING_FRUIT, GATHERING_DURATION_MS)
		agent.inventory[Item.FOOD.ordinal]++
		return true
	}

	if (FeatureAspect.FOOD_SOURCE_MUSHROOMS in aspects) {
		memorize(MemoryType.FOOD_SOURCE_POSITION, myPos)

		delay(AgentActivity.GATHERING_MUSHROOMS, GATHERING_DURATION_MS)
		agent.inventory[Item.FOOD.ordinal]++
		return true
	}

	if (!agent.isBaby && FeatureAspect.FOOD_SOURCE_WILD_ANIMALS in aspects) {
		memorize(MemoryType.FOOD_SOURCE_POSITION, myPos)
		// TODO(jp): Skill check
		delay(AgentActivity.HUNTING, HUNTING_DURATION_MS)
		agent.inventory[Item.FOOD.ordinal] += 7
		return true
	}

	if (FeatureAspect.FOOD_SOURCE_SMALL_WILD_ANIMALS in aspects) {
		memorize(MemoryType.FOOD_SOURCE_POSITION, myPos)
		// TODO(jp): Skill check
		delay(AgentActivity.HUNTING, HUNTING_SMALL_DURATION_MS)
		agent.inventory[Item.FOOD.ordinal] += 5
		return true
	}

	return false
}

/** Check if a cognitive function should fail, due to lack of sleep, water, panic, etc. */
fun AIContext.misfire():Boolean {
	val attributes = attributes()
	val health = 10 - attributes[AgentAttribute.HEALTH]
	val food = -50 - attributes[AgentAttribute.HUNGER]
	val water = -10 - attributes[AgentAttribute.THIRST]
	val sleep = 20 - attributes[AgentAttribute.SLEEP]
	val panic = 30 - (attributes[AgentAttribute.ALERTNESS] - attributes[AgentAttribute.MENTAL_STRENGTH])

	val misfireChance = max(max(max(health, food), max(water, sleep)), panic)
	if (misfireChance <= 0) {
		return false
	}
	return misfireChance > Random.nextInt(100)
}

/** Check if the agent is getting uncomfortable and should re-evaluate life choices. */
fun AIContext.isGettingUncomfortable():Boolean {
	val attributes = attributes()
	val thirst = attributes[AgentAttribute.THIRST]
	val hunger = attributes[AgentAttribute.HUNGER]
	val sleepiness = attributes[AgentAttribute.SLEEP]
	return misfire() || thirst < 20 || hunger < 20 || sleepiness < 20
}

suspend fun AIContext.sleep() {
	val attributes = attributes()
	val sleepForPoints = max(AgentAttribute.SLEEP.max - attributes[AgentAttribute.SLEEP], 10)

	for (i in 0 until sleepForPoints) {
		delay(AgentActivity.SLEEPING, SLEEP_DURATION_MS_PER_POINT)
		val sleepAttr = attributes[AgentAttribute.SLEEP] + 1
		attributes[AgentAttribute.SLEEP] = sleepAttr

		val waterNeed = attributes[AgentAttribute.THIRST].toInt()
		val foodNeed = attributes[AgentAttribute.HUNGER].toInt()
		if ((waterNeed < 10 || foodNeed < 10) && (sleepAttr > -30)) {
			// Can't sleep when really thirsty or hungry (and not super sleep deprived)
			break
		}
	}
}

suspend fun AIContext.talkWith(entity:Int):Boolean {
	// TODO somehow engage the other entity into this?
	val otherAgentC = agentMapper[entity] ?: return false
	if (!otherAgentC.activity.canListen) {
		return false
	}
	val otherPositionC = positionMapper[entity] ?: return false
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
			delay(AgentActivity.CHATTING, TALK_DURATION_MS_PER_POINT)
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

	delay(AgentActivity.CRAFTING, duration)
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
