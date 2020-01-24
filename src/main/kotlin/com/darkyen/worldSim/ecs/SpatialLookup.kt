package com.darkyen.worldSim.ecs

import com.badlogic.gdx.utils.LongMap
import com.darkyen.worldSim.util.Vec2
import com.darkyen.worldSim.util.forEach
import com.github.antag99.retinazer.EntitySet
import com.github.antag99.retinazer.Family
import com.github.antag99.retinazer.Mapper
import com.github.antag99.retinazer.Wire
import com.github.antag99.retinazer.systems.FamilyWatcherSystem
import com.github.antag99.retinazer.util.Bag
import com.github.antag99.retinazer.util.Mask

/**
 *
 */
abstract class SpatialLookupService(family: Family) : FamilyWatcherSystem.Single(family) {

	init {
		assert(family.requires(PositionC::class.java))
	}

	@PublishedApi
	@Wire
	internal lateinit var positionC: Mapper<PositionC>

	@PublishedApi
	internal val entities: Mask
		get() = engine.entities.mask

	val chunks = LongMap<EntitySet>()
	private val entityChunkSets = Bag<EntitySet?>()

	fun onEntityMovedAcrossChunks(entity:Int) {
		removedEntity(entity)
		insertedEntity(entity)
	}

	override fun insertedEntity(entity: Int) {
		val position = positionC[entity] ?: return
		val pos = position.pos
		val chunkKey = pos.chunkKey
		var chunkSet = chunks.get(chunkKey)
		if (chunkSet == null) {
			chunkSet = EntitySet()
			chunks.put(chunkKey, chunkSet)
		}

		chunkSet.addEntity(entity)
		entityChunkSets.set(entity, chunkSet)
	}

	override fun removedEntity(entity: Int) {
		entityChunkSets.remove(entity)?.removeEntity(entity)
	}

	inline fun forEntitiesInChunksSpanningRectangle(low:Vec2, high: Vec2, action:(entity:Int) -> Unit) {
		val entities = entities
		for (chunkY in high.chunkY downTo low.chunkY) {
			for (chunkX in low.chunkX..high.chunkX) {
				val chunkEntitySet = chunks.get(Vec2.ofChunkCorner(chunkX, chunkY).chunkKey) ?: continue
				chunkEntitySet.indices.forEach { entity ->
					if (entities.get(entity)) {
						action(entity)
					}
				}
			}
		}
	}

	inline fun forEntitiesNear(center:Vec2, maxManhattanDistance:Int, action:(entity:Int, distance:Int) -> Unit) {
		val positionC = positionC
		val centerX = center.x
		val centerY = center.y

		val firstChunkX = (centerX - maxManhattanDistance) shr CHUNK_SIZE_SHIFT
		val lastChunkX = (centerX + maxManhattanDistance) shr CHUNK_SIZE_SHIFT
		val firstChunkY = (centerY - maxManhattanDistance) shr CHUNK_SIZE_SHIFT
		val lastChunkY = (centerY + maxManhattanDistance) shr CHUNK_SIZE_SHIFT
		for (chunkX in firstChunkX .. lastChunkX) {
			for (chunkY in firstChunkY .. lastChunkY) {
				val chunkEntitySet = chunks.get(Vec2.ofChunkCorner(chunkX, chunkY).chunkKey) ?: continue
				chunkEntitySet.indices.forEach { entity ->
					val position = positionC[entity] ?: return@forEach
					val dist = (position.pos - center).manhLen
					if (dist > maxManhattanDistance) {
						return@forEach
					}

					action(entity, dist)
				}
			}
		}
	}
}
