package com.darkyen.worldSim.ecs

import com.darkyen.worldSim.TileType
import com.darkyen.worldSim.ai.WorldPathFinder
import com.darkyen.worldSim.util.DIRECTION_VECTORS
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
		return pathFinder.findPathInTimeLimit(from, to, longArrayOf(to.packed), 1_000_000 /* 1 ms */)
	}

	/** Find a path from [from] to any tile near [toNear]. */
	fun findPathNear(from:Vec2, toNear:Vec2): WorldPathFinder.Path? {
		val nearbyPositions = LongArray(4) { (toNear + Vec2(DIRECTION_VECTORS[it])).packed }

		// Early out on unreachable destinations
		if (nearbyPositions.all { to -> world.getTile(Vec2(to)).type != TileType.LAND }) {
			return null
		}

		val pathFinder = pathFinder.get()
		return pathFinder.findPathInTimeLimit(from, toNear, nearbyPositions, 1_000_000 /* 1 ms */)
	}
}