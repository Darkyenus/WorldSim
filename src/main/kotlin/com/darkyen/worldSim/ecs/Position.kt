package com.darkyen.worldSim.ecs

import com.badlogic.gdx.math.Vector2
import com.darkyen.worldSim.util.Direction
import com.darkyen.worldSim.util.forEach
import com.github.antag99.retinazer.Component
import com.github.antag99.retinazer.Mapper
import com.github.antag99.retinazer.Wire
import com.github.antag99.retinazer.systems.FamilyPresenceWatcherSystem
import kotlin.math.min

/** Position component. */
class PositionC : Component {
    var pos = Vec2(0, 0)
	var movement: Direction = Direction.NONE
	var progress = 0f
	/** Movement speed in tiles per second. */
	var speed = 1f

	/** Movement that should be done after this one is completed  */
	var nextMovement: Direction = Direction.NONE

	fun getX(): Float {
		return pos.x + movement.deltaX * progress
	}

	fun getY(): Float {
		return pos.y + movement.deltaY * progress
	}

	fun getPosition(to: Vector2): Vector2 {
		return to.set(pos.x + movement.deltaX * progress, pos.y + movement.deltaY * progress)
	}
}

/** Position system. Moves [PositionC]omponents and updates [World.Chunk.entities]. */
class PositionS : FamilyPresenceWatcherSystem.Single(COMPONENT_DOMAIN.familyWith(PositionC::class.java)) {

	@Wire
	private lateinit var positionMapper: Mapper<PositionC>
	@Wire
	private lateinit var world: World

	override fun update(delta: Float) {
		super.update(delta)
		val world = world
		familyEntities.indices.forEach { entity ->
			val position = positionMapper[entity]!!
			val movement = position.movement
			if (movement == Direction.NONE) {
				return@forEach
			}

			val newProgress = position.progress + delta * position.speed
			if (newProgress < 1f) {
				position.progress = newProgress
				return@forEach
			}
			// Move tile!
			val oldChunkKey = position.pos.chunkKey
			position.pos += movement.vec
			position.progress = min(newProgress - 1f, 1f)
			position.movement = position.nextMovement
			position.nextMovement = Direction.NONE
			val newChunkKey = position.pos.chunkKey

			if (oldChunkKey != newChunkKey) {
				world.getChunk(oldChunkKey)?.entities?.removeEntity(entity)
				world.getChunk(newChunkKey)?.entities?.addEntity(entity)
			}
		}
	}

	override fun insertedEntity(entity: Int, delta: Float) {
		val position = positionMapper[entity]!!
		world.getChunk(position.pos)?.entities?.addEntity(entity)
	}

	override fun removedEntity(entity: Int, delta: Float) {
		val position = positionMapper[entity]!!
		world.getChunk(position.pos)?.entities?.removeEntity(entity)
	}
}