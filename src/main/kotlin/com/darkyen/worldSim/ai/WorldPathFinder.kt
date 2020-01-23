/* *****************************************************************************
 * Copyright 2014 gdx-ai See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.darkyen.worldSim.ai

import com.badlogic.gdx.utils.BinaryHeap
import com.darkyen.worldSim.TileType
import com.darkyen.worldSim.ecs.WORLD_SIZE
import com.darkyen.worldSim.ecs.WORLD_SIZE_MASK
import com.darkyen.worldSim.ecs.WORLD_SIZE_SHIFT
import com.darkyen.worldSim.ecs.World
import com.darkyen.worldSim.util.GdxLongArray
import com.darkyen.worldSim.util.Vec2
import com.darkyen.worldSim.util.forPositionsAround

/**
 * Based on IndexedAStarPathFinder from gdx-ai project.
 * Optimized for [com.darkyen.worldSim.ecs.World] path finding.
 */
class WorldPathFinder(private val world:World) {

	private val nodeRecords: Array<NodeRecord?> = arrayOfNulls(WORLD_SIZE * WORLD_SIZE)
	private val openList: BinaryHeap<NodeRecord> = BinaryHeap()
	/** The unique ID for each search run. Used to mark nodes.  */
	private var searchId = 0

	fun findPath(from: Vec2, to: Vec2): Path? {
		initSearch(from, to)
		val openList = openList
		do {
			// Retrieve the node with smallest estimated total cost from the open list
			val current:NodeRecord = openList.pop()
			current.category = CLOSED
			// Terminate if we reached the goal node
			if (current.node == to) {
				return generateNodePath(to)
			}
			visitChildren(current, to)
		} while (openList.size > 0)
		// We've run out of nodes without finding the goal, so there's no solution
		return null
	}

	fun findPathInTimeLimit(from: Vec2, to: Vec2, maxTimeNanos:Long): Path? {
		val endTime = System.nanoTime() + maxTimeNanos

		initSearch(from, to)
		val openList = openList
		var iteration = 0
		do {
			// Retrieve the node with smallest estimated total cost from the open list
			val current:NodeRecord = openList.pop()
			current.category = CLOSED
			// Terminate if we reached the goal node
			if (current.node == to) {
				return generateNodePath(to)
			}

			if ((++iteration and 0b1111) == 0 && System.nanoTime() >= endTime) {
				// Timed out
				return null
			}

			visitChildren(current, to)
		} while (openList.size > 0)
		// We've run out of nodes without finding the goal, so there's no solution
		return null
	}

	fun findPathWithMaxComplexity(from: Vec2, to: Vec2, maxComplexityCostFactor:Float): Path? {
		val maxCost = estimateDistance(from, to) * maxComplexityCostFactor

		initSearch(from, to)
		val openList = openList
		do {
			// Retrieve the node with smallest estimated total cost from the open list
			val current:NodeRecord = openList.pop()
			current.category = CLOSED
			// Terminate if we reached the goal node
			if (current.node == to) {
				return generateNodePath(to)
			}

			if (current.costSoFar > maxCost) {
				// Too costly to find
				return null
			}

			visitChildren(current, to)
		} while (openList.size > 0)
		// We've run out of nodes without finding the goal, so there's no solution
		return null
	}

	private fun generateNodePath(endNode: Vec2):Path {
		val outPath = PathImpl()

		// Work back along the path, accumulating nodes
		var current = nodeRecords[endNode.graphIndex]!!
		while (current.from != Vec2.NULL) {
			outPath.add(current.node.packed)
			current = nodeRecords[current.from.graphIndex]!!
		}
		// Reverse the path
		outPath.reverse()
		return outPath
	}

	private fun initSearch(startNode: Vec2, endNode: Vec2) {
		// Increment the search id
		searchId++
		// Initialize the open list
		openList.clear()
		// Initialize the record for the start node and add it to the open list
		val startRecord = getNodeRecord(startNode)
		startRecord.from = Vec2.NULL
		startRecord.costSoFar = 0f
		addToOpenList(startRecord, estimateDistance(startNode, endNode))
	}

	private inline fun forConnections(from:Vec2, connection:(to:Vec2, cost:Float) -> Unit) {
		val cost = 1f / world.getMovementSpeedMultiplier(from)
		forPositionsAround(from) { pos ->
			if (world.getTile(pos).type == TileType.LAND) {
				connection(pos, cost)
			}
		}
	}

	private fun visitChildren(current: NodeRecord, endNode: Vec2) {
		val from = current.node
		forConnections(from) { to, cost ->
			val nodeCost = current.costSoFar + cost
			val nodeRecord = getNodeRecord(to)
			val nodeHeuristic = if (nodeRecord.category == CLOSED) {
				// The node is closed If we didn't find a shorter route, skip
				if (nodeRecord.costSoFar <= nodeCost) return@forConnections
				// We can use the node's old cost values to calculate its heuristic
				// without calling the possibly expensive heuristic function
				nodeRecord.value - nodeRecord.costSoFar
			} else if (nodeRecord.category == OPEN) {
				// If our route is no better, then skip
				if (nodeRecord.costSoFar <= nodeCost) return@forConnections
				// Remove it from the open list (it will be re-added with the new cost)
				openList.remove(nodeRecord)
				// We can use the node's old cost values to calculate its heuristic
				// without calling the possibly expensive heuristic function
				nodeRecord.value - nodeRecord.costSoFar
			} else { // the node is unvisited
				// We'll need to calculate the heuristic value using the function,
				// since we don't have a node record with a previously calculated value
				estimateDistance(to, endNode)
			}
			// Update node record's cost and connection
			nodeRecord.costSoFar = nodeCost
			nodeRecord.from = from
			// Add it to the open list with the estimated total cost
			addToOpenList(nodeRecord, nodeCost + nodeHeuristic)
		}
	}

	private fun addToOpenList(nodeRecord: NodeRecord?, estimatedTotalCost: Float) {
		openList.add(nodeRecord, estimatedTotalCost)
		nodeRecord!!.category = OPEN
	}

	private fun getNodeRecord(node: Vec2): NodeRecord {
		val index = node.graphIndex
		var nr = nodeRecords[index]
		if (nr != null) {
			if (nr.searchId != searchId) {
				nr.category = UNVISITED
				nr.searchId = searchId
			}
			return nr
		}
		nr = NodeRecord(node)
		nr.searchId = searchId
		nodeRecords[index] = nr
		return nr
	}

	private class NodeRecord(val node:Vec2) : BinaryHeap.Node(0f) {
		/** The incoming connection to the node  */
		var from: Vec2 = Vec2.NULL
		/** The actual cost from the start node.  */
		var costSoFar = 0f
		/** The node category: [UNVISITED], [OPEN] or [CLOSED].  */
		var category = 0
		/** ID of the current search.  */
		var searchId = 0
	}

	interface Path {
		val length:Int
		fun node(i:Int):Vec2
	}

	private class PathImpl : GdxLongArray(), Path {
		override val length: Int
			get() = size

		override fun node(i: Int): Vec2 {
			return Vec2(items[i])
		}
	}

	private companion object {
		private const val UNVISITED = 0
		private const val OPEN = 1
		private const val CLOSED = 2

		val Vec2.graphIndex:Int
			get() = (x shl WORLD_SIZE_SHIFT) or (y and WORLD_SIZE_MASK)

		fun estimateDistance(from:Vec2, to:Vec2):Float = (from - to).manhLen.toFloat()
	}
}