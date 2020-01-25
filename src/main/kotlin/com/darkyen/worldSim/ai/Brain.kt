package com.darkyen.worldSim.ai

import com.darkyen.worldSim.FOOD_SOURCES
import com.darkyen.worldSim.FeatureAspect
import com.darkyen.worldSim.Item
import com.darkyen.worldSim.TileType
import com.darkyen.worldSim.ecs.AIContext
import com.darkyen.worldSim.ecs.AgentActivity
import com.darkyen.worldSim.ecs.AgentAttribute.*
import com.darkyen.worldSim.ecs.HOUR_LENGTH_IN_MS
import com.darkyen.worldSim.ecs.MemoryType
import com.darkyen.worldSim.ecs.get
import com.darkyen.worldSim.ecs.say
import com.darkyen.worldSim.util.DIRECTIONS
import com.darkyen.worldSim.util.Vec2
import com.darkyen.worldSim.util.find
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

suspend fun AIContext.brain() {
	if (!takeCareOfBasicNeeds(20)) {
		// Not met, time to panic...
		increaseAlert(10)
		if (agent.misfire()) {
			// PANIC
			panic()
		}

		delay(AgentActivity.PONDERING, 1000)
		return
	}

	stockUpOnMaterials()

	delay(AgentActivity.PONDERING, 1000)
}

private suspend fun AIContext.takeCareOfBasicNeeds(howMuch:Byte):Boolean {
	// Find the most pressing problem
	val attributes = attributes()
	val waterNeed = attributes[THIRST].toInt() - 30
	val foodNeed = attributes[HUNGER].toInt() - 20
	val sleepNeed = attributes[SLEEP].toInt()
	val socialNeed = max(attributes[SOCIAL] + 50 - attributes[EXTROVERSION] / 2, 30)

	if (waterNeed < howMuch && foodNeed < howMuch && sleepNeed < howMuch && socialNeed < howMuch) {
		return true
	}

	class Need(val level:Int, val dealWithIt:AITask) : Comparable<Need> {
		override fun compareTo(other: Need): Int = level.compareTo(other.level)
	}
	val needs = arrayOf(
			Need(waterNeed, AIContext::seekWater),
			Need(foodNeed, AIContext::seekFood),
			Need(sleepNeed, AIContext::seekSleep),
			Need(socialNeed, AIContext::seekSocial)
	)
	needs.sort()

	var success = true
	for (need in needs) {
		if (need.level > howMuch) {
			break
		}
		if (!need.dealWithIt.invoke(this)) {
			success = false
		}
	}

	return success
}

enum class Seekable(val goNearOnly:Boolean, val memory:MemoryType) {
	WATER(true, MemoryType.WATER_SOURCE_POSITION) {
		override fun acceptable(context: AIContext, pos: Vec2): Boolean = context.tileAt(pos).type == TileType.WATER
	},
	FOOD(false, MemoryType.FOOD_SOURCE_POSITION) {
		override fun acceptable(context: AIContext, pos: Vec2): Boolean {
			val features = context.featureAt(pos) ?: return false
			val aspects = features.aspects
			return FOOD_SOURCES.any { it in aspects }
		}
	},
	WOOD(false, MemoryType.WOOD_SOURCE_POSITION) {
		override fun acceptable(context: AIContext, pos: Vec2): Boolean {
			val features = context.featureAt(pos) ?: return false
			val aspects = features.aspects
			return FeatureAspect.WOOD_SOURCE in aspects
		}
	},
	CRAFTING_MATERIAL(false, MemoryType.CRATING_MATERIAL_SOURCE_POSITION) {
		override fun acceptable(context: AIContext, pos: Vec2): Boolean {
			val features = context.featureAt(pos) ?: return false
			val aspects = features.aspects
			return FeatureAspect.CRAFTING_MATERIAL_SOURCE in aspects
		}
	},
	STONE(false, MemoryType.STONE_SOURCE_POSITION) {
		override fun acceptable(context: AIContext, pos: Vec2): Boolean {
			val features = context.featureAt(pos) ?: return false
			val aspects = features.aspects
			return FeatureAspect.STONE_SOURCE in aspects
		}
	}
	;

	abstract fun acceptable(context: AIContext, pos:Vec2):Boolean
}

private fun AIContext.lookAroundForAcceptableTile(seekable:Seekable):Vec2 {
	val basePos = position.pos

	for (x in -MAX_LOOK_DISTANCE .. MAX_LOOK_DISTANCE) {
		val remainingForY = MAX_LOOK_DISTANCE - abs(x)
		for (y in -remainingForY .. remainingForY) {
			val pos = basePos + Vec2(x, y)
			if (seekable.acceptable(this, pos)) {
				return pos
			}
		}
	}

	return Vec2.NULL
}

/** Try to go to a place that  */
private suspend fun AIContext.seek(seekable:Seekable, timeLimitMs:Long):Boolean {
	val timeToStop = currentTimeMs + timeLimitMs

	val myPos = position.pos
	var checkMemory = true

	var direction = DIRECTIONS.random()
	while (true) {
		// Check nearby tiles
		val nearbyTile = lookAroundForAcceptableTile(seekable)
		if (nearbyTile != Vec2.NULL) {
			return walkTo(nearbyTile, seekable.goNearOnly)
		}

		// Check memory
		if (checkMemory) {
			checkMemory = false
			val recallNearest = recallNearest(seekable.memory)

			if (recallNearest == Vec2.NULL || recallNearest.manhDst(myPos) > 50) {
				// Ask others TODO Check if anybody is even nearby...
				say(seekable.memory, Vec2.NULL)
			}

			if (recallNearest != Vec2.NULL) {
				if (walkTo(recallNearest)) {
					// Skip wandering
					continue
				}
			}
		}

		// Check time limit
		if (currentTimeMs > timeToStop) {
			return false
		}

		// Wander around for a few tiles, turning on obstacles
		for (i in 0 until 5) {
			if (!walk(direction)) {
				direction = if (Random.nextBoolean()) direction.left else direction.right
			}
		}
	}
}

private suspend fun AIContext.seekWater():Boolean {
	if (drinkFromEnvironment() || drinkFromInventory()) {
		return true
	}

	// Look for water at known locations / at home
	// TODO

	// Look for water at known village centers
	// TODO

	// Look for water
	if (seek(Seekable.WATER, 20_000)) {
		return drinkFromEnvironment()
	}

	return false
}

private suspend fun AIContext.seekFood():Boolean {
	if (eatFromInventory()) {
		return true
	}

	return obtainFood() && eatFromInventory()
}

private suspend fun AIContext.obtainFood():Boolean {
	// Look for food at home
	// TODO

	// Buy food from others
	// TODO

	// Look for food
	if (seek(Seekable.FOOD, 20_000)) {
		return gatherFoodFromEnvironment()
	}

	return false
}

private suspend fun AIContext.seekSleep():Boolean {
	// TODO(jp): Go to sleep at home

	// TODO(jp): Check for enemies

	sleep()
	return true
}

private suspend fun AIContext.seekSocial():Boolean {
	val nearbyEntities = findNearbyAgents()
	if (nearbyEntities.size == 0) {
		// Nobody is nearby
		return false
	}

	// Pick someone that is not doing anything important
	val agentC = agentMapper
	val idleNearbyEntity = nearbyEntities.find { entity ->
		val agent = agentC[entity] ?: return@find false
		agent.activity.canListen
	}

	if (idleNearbyEntity == -1) {
		return false
	}

	val positionC = positionMapper[idleNearbyEntity] ?: return false
	if (!walkTo(positionC.pos, true)) {
		return false
	}

	return talkWith(idleNearbyEntity)
}

private suspend fun AIContext.panic() {
	for (i in 0 until 10) {
		walkTo(position() + Vec2(Random.nextInt(-5, 6), Random.nextInt(-5, 6)), activity = AgentActivity.PANICKING)
	}
}

private suspend fun AIContext.gatherMaterialsAndCraftItem(item:Item):Boolean {
	val requiredMaterialCount = item.craftMaterialRequirement
	assert(requiredMaterialCount > 0)
	val missingCraftingMaterials = requiredMaterialCount - inventoryCount(Item.CRAFTING_MATERIAL)
	for (i in 0 until missingCraftingMaterials) {
		if (!obtainCraftMaterial()) {
			return false
		}
	}

	return craftItem(item)
}

private suspend fun AIContext.obtainCraftMaterial():Boolean {
	return seek(Seekable.CRAFTING_MATERIAL, HOUR_LENGTH_IN_MS * 4L)
			&& gatherCraftingResourcesFromEnvironment()
}

private suspend fun AIContext.obtainWood():Boolean {
	return seek(Seekable.WOOD, HOUR_LENGTH_IN_MS * 4L)
			&& gatherWoodFromEnvironment()
}

private suspend fun AIContext.obtainStone():Boolean {
	return seek(Seekable.STONE, HOUR_LENGTH_IN_MS * 4L)
			&& gatherStoneFromEnvironment()
}

private suspend fun AIContext.stockUpOnMaterials() {
	// Gather more food
	if (inventoryCount(Item.FOOD) < 3) {
		obtainFood()
		return
	}

	// Refill canteens
	if (inventoryCount(Item.WATER_CANTEEN_EMPTY) > 0) {
		if (seek(Seekable.WATER, 20_000)) {
			while (inventoryCount(Item.WATER_CANTEEN_EMPTY) > 0) {
				if (!refillCanteen()) {
					break
				}
			}
			return
		}
	}

	// Craft canteens
	if (inventoryCount(Item.WATER_CANTEEN_EMPTY) + inventoryCount(Item.WATER_CANTEEN_FULL) < 1) {
		gatherMaterialsAndCraftItem(Item.WATER_CANTEEN_EMPTY)
		return
	}

	// TODO(jp): Temporary
	if (inventoryCount(Item.WOOD) < 100) {
		obtainWood()
		return
	}

	if (inventoryCount(Item.STONE) < 100) {
		obtainStone()
		return
	}
}