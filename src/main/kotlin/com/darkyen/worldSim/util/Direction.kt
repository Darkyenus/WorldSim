package com.darkyen.worldSim.util

import com.darkyen.worldSim.ecs.Vec2

/** A movement direction. */
enum class Direction(val deltaX:Int, val deltaY:Int) {
	NONE(0, 0),
	LEFT(-1, 0),
	RIGHT(1, 0),
	UP(0, 1),
	DOWN(0, -1);

	val vec = Vec2(deltaX, deltaY)
}

/** A side of tile. */
enum class Side(val direction:Direction) {
	LEFT(Direction.LEFT),
	RIGHT(Direction.RIGHT),
	TOP(Direction.UP),
	BOTTOM(Direction.DOWN)
}