package com.darkyen.worldSim

import com.darkyen.worldSim.FeatureAspect.*
import com.darkyen.worldSim.Sprite.Connecting
import com.darkyen.worldSim.Sprite.Simple
import com.darkyen.worldSim.util.Direction
import java.util.*

/**
 * Represents tile base. Mostly visual, but affects things like collisions.
 */
enum class Tile(val type:TileType, val sprite:Sprite) {
	GRASS(TileType.LAND, Simple(1)),
	FOREST(TileType.LAND, Simple(83)),
	DESERT(TileType.LAND, Simple(84)),
	SNOW(TileType.LAND, Simple(85)),
	WATER(TileType.WATER, Simple(30)),
	ROCK(TileType.INACCESSIBLE, Simple(31))
}

enum class TileType {
	LAND,
	INACCESSIBLE,
	WATER
}

/**
 *
 */
enum class Feature(
		val sprite:Sprite,
		vararg aspects:FeatureAspect) {
	ROAD(Connecting(
			13, // 0 : NONE
			25, // 1 : TOP
			26, // 2 : RIGHT
			21, // 3 : TOP RIGHT
			28, // 4 : LEFT
			24, // 5 : TOP LEFT
			19, // 6 : RIGHT LEFT
			17, // 7 : TOP RIGHT LEFT
			27, // 8 : BOTTOM
			20, // 9 : TOP BOTTOM
			22, // 10: RIGHT BOTTOM
			18, // 11: TOP RIGHT BOTTOM
			23, // 12: LEFT BOTTOM
			16, // 13: TOP LEFT BOTTOM
			15, // 14: RIGHT LEFT BOTTOM
			14 // 15: TOP RIGHT LEFT BOTTOM
	), WALK_FASTER),
	CONIFEROUS_FOREST_DEEP(Simple(32), FOOD_SOURCE_WILD_ANIMALS, FOOD_SOURCE_MUSHROOMS, WOOD_SOURCE, WALK_MUCH_SLOWER),
	CONIFEROUS_FOREST(Simple(37), FOOD_SOURCE_MUSHROOMS, WOOD_SOURCE, WALK_SLOWER),
	DECIDUOUS_FOREST_DEEP(Simple(32), FOOD_SOURCE_WILD_ANIMALS, FOOD_SOURCE_MUSHROOMS, WOOD_SOURCE, WALK_MUCH_SLOWER),
	DECIDUOUS_FOREST(Simple(35), FOOD_SOURCE_MUSHROOMS, WOOD_SOURCE, WALK_SLOWER),
	DECIDUOUS_FRUIT_FOREST(Simple(34), FOOD_SOURCE_FRUIT, WOOD_SOURCE, WALK_SLOWER),
	BUSHES(Simple(38), FOOD_SOURCE_SMALL_WILD_ANIMALS, WALK_SLOWER),
	BERRY_BUSHES(Simple(36), FOOD_SOURCE_SMALL_WILD_ANIMALS, FOOD_SOURCE_FRUIT, WALK_SLOWER),

	HOUSE_1(Simple(2), HOUSE_HOME) {
		override fun connectsTo(other: Feature, ownSide: Direction): Boolean = ownSide === Direction.DOWN && other === ROAD
	},
	HOUSE_2(Simple(3), HOUSE_HOME) {
		override fun connectsTo(other: Feature, ownSide: Direction): Boolean = ownSide === Direction.DOWN && other === ROAD
	},
	HOUSE_3(Simple(4), HOUSE_HOME) {
		override fun connectsTo(other: Feature, ownSide: Direction): Boolean = ownSide === Direction.DOWN && other === ROAD
	},
	HOUSE_4(Simple(5), HOUSE_HOME) {
		override fun connectsTo(other: Feature, ownSide: Direction): Boolean = ownSide === Direction.DOWN && other === ROAD
	},
	WAREHOUSE_1(Simple(6), HOUSE_WAREHOUSE) {
		override fun connectsTo(other: Feature, ownSide: Direction): Boolean = ownSide === Direction.DOWN && other === ROAD
	},
	WAREHOUSE_2(Simple(8), HOUSE_WAREHOUSE) {
		override fun connectsTo(other: Feature, ownSide: Direction): Boolean = ownSide === Direction.DOWN && other === ROAD
	},
	TOWN_HALL_1(Simple(7), HOUSE_TOWN_HALL) {
		override fun connectsTo(other: Feature, ownSide: Direction): Boolean = ownSide === Direction.DOWN && other === ROAD
	},
	;

	open fun connectsTo(other:Feature, ownSide: Direction):Boolean {
		return other === this
	}

	val aspects = EnumSet.noneOf(FeatureAspect::class.java).also { it.addAll(aspects) }
}

enum class FeatureAspect {
	WALK_FASTER,
	WALK_SLOWER,
	WALK_MUCH_SLOWER,
	FOOD_SOURCE_FRUIT,
	FOOD_SOURCE_WILD_ANIMALS,
	FOOD_SOURCE_SMALL_WILD_ANIMALS,
	FOOD_SOURCE_MUSHROOMS,
	WOOD_SOURCE,

	HOUSE_HOME,
	HOUSE_WAREHOUSE,
	HOUSE_TOWN_HALL,
}

enum class Item {
	FOOD,
	WOOD;
}

val ITEMS = Item.values()

sealed class Sprite {

	/** A simple tile sprite, possibly with multiple variants. */
	class Simple(vararg val variants:Int) : Sprite()

	/** Special drawing if it has the same tile as a neighbor.
	 * @param connections 2^4=16 element array of sprites, each bit signifies different connectivity direction.
	 * 1=TOP
	 * 2=RIGHT
	 * 4=LEFT
	 * 8=BOTTOM */
	// 0 : NONE
	// 1 : TOP
	// 2 : RIGHT
	// 3 : TOP RIGHT
	// 4 : LEFT
	// 5 : TOP LEFT
	// 6 : RIGHT LEFT
	// 7 : TOP RIGHT LEFT
	// 8 : BOTTOM
	// 9 : TOP BOTTOM
	// 10: RIGHT BOTTOM
	// 11: TOP RIGHT BOTTOM
	// 12: LEFT BOTTOM
	// 13: TOP LEFT BOTTOM
	// 14: RIGHT LEFT BOTTOM
	// 15: TOP RIGHT LEFT BOTTOM
	class Connecting(vararg val connections:Int) : Sprite() {
		init { assert(connections.size == 16) }
	}
}