package com.darkyen.worldSim.ai

import com.darkyen.worldSim.FOOD_SOURCES
import com.darkyen.worldSim.FeatureAspect
import com.darkyen.worldSim.TileType
import com.darkyen.worldSim.ecs.AgentActivity
import com.darkyen.worldSim.ecs.AgentAttribute.*
import com.darkyen.worldSim.ecs.get
import com.darkyen.worldSim.util.DIRECTIONS
import com.darkyen.worldSim.util.Vec2
import com.darkyen.worldSim.util.forDirections
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

suspend fun AIContext.brain() {
	if (!takeCareOfBasicNeeds(20)) {
		// Not met, time to panic...
		increaseAlert(10)
		if (agent.misfire()) {
			// PANIC
			panic()
		}
	}

	delay(1000)
}

private suspend fun AIContext.takeCareOfBasicNeeds(howMuch:Byte):Boolean {
	// Find the most pressing problem
	val attributes = attributes()
	val waterNeed = attributes[THIRST].toInt()
	val foodNeed = attributes[HUNGER].toInt()
	val sleepNeed = attributes[SLEEP].toInt()
	val socialNeed = attributes[SOCIAL] + 50 - attributes[EXTROVERSION]

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

enum class Seekable(val goNearOnly:Boolean) {
	WATER(true) {
		override fun acceptable(context:AIContext,pos: Vec2): Boolean = context.agentS.world.getTile(pos).type == TileType.WATER
	},
	FOOD(false) {
		override fun acceptable(context:AIContext,pos: Vec2): Boolean {
			val features = context.agentS.world.getFeature(pos) ?: return false
			val aspects = features.aspects
			return FOOD_SOURCES.any { it in aspects }
		}
	},
	WOOD(false) {
		override fun acceptable(context:AIContext,pos: Vec2): Boolean {
			val features = context.agentS.world.getFeature(pos) ?: return false
			val aspects = features.aspects
			return FeatureAspect.WOOD_SOURCE in aspects
		}
	}
	;

	abstract fun acceptable(context:AIContext, pos:Vec2):Boolean
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

	var direction = DIRECTIONS.random()
	while (true) {
		// Check nearby tiles
		val nearbyTile = lookAroundForAcceptableTile(seekable)
		if (nearbyTile != Vec2.NULL) {
			return walkTo(nearbyTile, seekable.goNearOnly)
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
	if (drinkFromInventory() || drinkFromEnvironment()) {
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

	// Look for food at home
	// TODO

	// Buy food from others
	// TODO

	// Look for food at known locations
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
	// TODO(jp): Implement
	return false
}

private suspend fun AIContext.panic() {
	for (i in 0 until 10) {
		walkTo(position() + Vec2(Random.nextInt(-5, 6), Random.nextInt(-5, 6)), activity = AgentActivity.PANICKING)
	}
}