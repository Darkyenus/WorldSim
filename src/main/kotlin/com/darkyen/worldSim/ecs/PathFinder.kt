package com.darkyen.worldSim.ecs

import com.darkyen.worldSim.TileType
import com.darkyen.worldSim.ai.WorldPathFinder
import com.darkyen.worldSim.util.Vec2
import com.github.antag99.retinazer.EngineService
import com.github.antag99.retinazer.Wire

/**
 *
 */
class PathFinder : EngineService {

	@Wire
	private lateinit var world:World

	private val pathFinder:ThreadLocal<WorldPathFinder> = ThreadLocal.withInitial {
		WorldPathFinder(world)
	}

	/**
	 * Find path between two points.
	 * Thread safe.
	 */
	fun findPath(from:Vec2, to:Vec2): WorldPathFinder.Path? {
		// Early out on unreachable destinations
		if (world.getTile(to).type != TileType.LAND) {
			return null
		}

		val pathFinder = pathFinder.get()
		return pathFinder.findPathInTimeLimit(from, to, 1_000_000 /* 1 ms */)
	}
}