package com.darkyen.worldSim.util

/** A movement direction. */
enum class Direction(val deltaX:Int, val deltaY:Int) {
	NONE(0, 0),
	LEFT(-1, 0),
	RIGHT(1, 0),
	UP(0, 1),
	DOWN(0, -1);

	val vec = Vec2(deltaX, deltaY)
}

operator fun Direction.times(scalar:Int):Vec2 {
	return vec * scalar
}

val DIRECTIONS = longArrayOf(
	Direction.LEFT.vec.packed,
	Direction.RIGHT.vec.packed,
	Direction.UP.vec.packed,
	Direction.DOWN.vec.packed
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
	for (side in DIRECTIONS) {
		action(Vec2(side))
	}
}

inline fun forPositionsAround(base:Vec2, action:(Vec2)->Unit) {
	for (side in DIRECTIONS) {
		action(base + Vec2(side))
	}
}