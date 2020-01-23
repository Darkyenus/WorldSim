package com.darkyen.worldSim.util

/** A movement direction. */
enum class Direction(val deltaX:Int, val deltaY:Int) {
	NONE(0, 0),
	UP(0, 1),
	RIGHT(1, 0),
	DOWN(0, -1),
	LEFT(-1, 0);

	val vec = Vec2(deltaX, deltaY)

	val right:Direction
		get() = DIRECTIONS[ordinal % 4]

	val left:Direction
		get() = DIRECTIONS[(ordinal + 2) % 4]
}

operator fun Direction.times(scalar:Int):Vec2 {
	return vec * scalar
}

val DIRECTIONS = arrayOf(
		Direction.UP,
		Direction.RIGHT,
		Direction.DOWN,
		Direction.LEFT
)

val DIRECTION_VECTORS = longArrayOf(
		Direction.UP.vec.packed,
		Direction.RIGHT.vec.packed,
		Direction.DOWN.vec.packed,
		Direction.LEFT.vec.packed
)

fun Vec2.directionTo(other:Vec2):Direction {
	val dir = other - this
	assert(dir.manhLen == 1)
	return when (dir.packed) {
		Direction.LEFT.vec.packed -> Direction.LEFT
		Direction.RIGHT.vec.packed -> Direction.RIGHT
		Direction.UP.vec.packed -> Direction.UP
		Direction.DOWN.vec.packed -> Direction.DOWN
		else -> throw AssertionError("Vectors are not adjacent: $this -> $other")
	}
}

inline fun forDirections(action:(Vec2)->Unit) {
	for (side in DIRECTION_VECTORS) {
		action(Vec2(side))
	}
}

inline fun forPositionsAround(base:Vec2, action:(Vec2)->Unit) {
	for (side in DIRECTION_VECTORS) {
		action(base + Vec2(side))
	}
}

inline fun anyPositionNearIs(base:Vec2, condition:(Vec2)->Boolean):Boolean {
	if (condition(base)) {
		return true
	}
	for (side in DIRECTION_VECTORS) {
		if (condition(base + Vec2(side))) {
			return true
		}
	}
	return false
}