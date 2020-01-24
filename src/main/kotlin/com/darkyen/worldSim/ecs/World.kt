package com.darkyen.worldSim.ecs

import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.LongMap
import com.darkyen.worldSim.Feature
import com.darkyen.worldSim.FeatureAspect
import com.darkyen.worldSim.ITEMS
import com.darkyen.worldSim.Item
import com.darkyen.worldSim.Sprite
import com.darkyen.worldSim.Tile
import com.darkyen.worldSim.WorldSim
import com.darkyen.worldSim.util.Direction
import com.darkyen.worldSim.util.GdxLongArray
import com.darkyen.worldSim.util.Vec2
import com.github.antag99.retinazer.Engine
import com.github.antag99.retinazer.EngineService
import com.github.antag99.retinazer.EntitySet
import com.github.antag99.retinazer.Wire

const val CHUNK_SIZE_SHIFT = 5
const val CHUNK_SIZE = 1 shl CHUNK_SIZE_SHIFT
private const val CHUNK_AREA = CHUNK_SIZE * CHUNK_SIZE
private const val CHUNK_SIZE_MASK = CHUNK_SIZE - 1
private const val CHUNK_KEY_MASK:Long = ((CHUNK_SIZE_MASK.toLong()) or (CHUNK_SIZE_MASK.toLong() shl 32)).inv()

const val WORLD_SIZE_SHIFT = 9
const val WORLD_SIZE = 1 shl WORLD_SIZE_SHIFT
const val WORLD_SIZE_MASK = WORLD_SIZE - 1
private const val WORLD_SIZE_RANGE_CHECK_MASK:Long = (WORLD_SIZE_MASK.toLong()) or (WORLD_SIZE_MASK.toLong() shl 32)

val Vec2.chunkKey:Long
	get() = packed and CHUNK_KEY_MASK

val Vec2.chunkX:Int
	get() = x shr CHUNK_SIZE_SHIFT

val Vec2.chunkY:Int
	get() = y shr CHUNK_SIZE_SHIFT

fun Vec2.Companion.ofChunkCorner(chunkX:Int, chunkY:Int):Vec2 = Vec2(chunkX shl CHUNK_SIZE_SHIFT, chunkY shl CHUNK_SIZE_SHIFT)

val Vec2.tileX:Int
	get() = x and CHUNK_SIZE_MASK

val Vec2.tileY:Int
	get() = y and CHUNK_SIZE_MASK

val Vec2.tileKey:Int
	get() {
		val tileX = tileX
		val tileY = tileY
		return (tileX shl CHUNK_SIZE_SHIFT) or tileY
	}

fun Vec2.atTile(tileKey:Int):Vec2 {
	val tileX = (tileKey shr CHUNK_SIZE_SHIFT) and CHUNK_SIZE_MASK
	val tileY = tileKey and CHUNK_SIZE_MASK
	val tileCoord = Vec2(tileX, tileY)
	return Vec2((this.packed and CHUNK_KEY_MASK) or tileCoord.packed)
}

/**
 *
 */
class World(
		/** Used to generate new chunks */
		private val generator: WorldGenerator,
		private val populator: ChunkPopulator
) : EngineService {

	@Wire
	private lateinit var engine: Engine

	private val chunks = LongMap<Chunk>()
	private val chunksToPopulate = GdxLongArray()

	/**
	 * Returns chunk at requested coordinates.
	 * When no chunk exists, one is created.
	 *
	 * Thread safe.
	 */
	fun getChunk(pos:Vec2): Chunk? {
		return getChunk(pos.chunkKey)
	}

	/**
	 * Returns chunk at requested chunk key.
	 * When no chunk exists, one is created.
	 *
	 * Thread safe.
	 */
	fun getChunk(chunkKey:Long):Chunk? {
		if ((chunkKey and WORLD_SIZE_RANGE_CHECK_MASK) != chunkKey) {
			return null
		}

		var result = chunks[chunkKey]
		if (result == null) {
			synchronized(generator) {
				result = chunks[chunkKey]
				if (result == null) {
					result = Chunk()
					generator.generateChunk(this, result, Vec2(chunkKey))
					chunks.put(chunkKey, result)
					chunksToPopulate.add(chunkKey)
				}
			}
		}
		return result
	}

	/** Get tile at given position. Thread safe. */
	fun getTile(pos:Vec2): Tile {
		val chunk: Chunk = getChunk(pos) ?: return Tile.WATER
		return chunk.tiles[pos.tileKey]
	}

	/** Set tile at given position.
	 * Thread safe **but prone to races**. */
	fun setTile(pos:Vec2, tile: Tile) {
		val chunk: Chunk = getChunk(pos) ?: return
		chunk.tiles[pos.tileKey] = tile
	}

	/** Get sprite for tile at given position.
	 * Thread safe. */
	fun getTileSprite(pos: Vec2): TextureRegion {
		val baseTile = getTile(pos)
		when (val baseSprite = baseTile.sprite) {
			is Sprite.Simple -> {
				val variants = baseSprite.variants
				if (variants.isEmpty()) {
					return WorldSim.sprites[0]
				}
				return WorldSim.sprites[variants[pos.packed.hashCode() % variants.size]]
			}
			is Sprite.Connecting -> {
				val topTile = getTile(pos + Vec2.UP)
				val rightTile = getTile(pos + Vec2.RIGHT)
				val leftTile = getTile(pos + Vec2.LEFT)
				val downTile = getTile(pos + Vec2.DOWN)

				val topConnect = if (topTile === baseTile) 1 else 0
				val rightConnect = if (rightTile === baseTile) 2 else 0
				val leftConnect = if (leftTile === baseTile) 4 else 0
				val downConnect = if (downTile === baseTile) 8 else 0

				val index = topConnect + rightConnect + leftConnect + downConnect
				return WorldSim.sprites[baseSprite.connections[index]]
			}
		}
	}

	/** Get sprite for tile feature of the tile at [pos].
	 * Thread safe. */
	fun getFeatureSprite(pos: Vec2): TextureAtlas.AtlasRegion? {
		val baseF = getFeature(pos) ?: return null
		when (val baseSprite = baseF.sprite) {
			is Sprite.Simple -> {
				val variants = baseSprite.variants
				if (variants.isEmpty()) {
					return null
				}
				return WorldSim.sprites[variants[pos.packed.hashCode() % variants.size]]
			}
			is Sprite.Connecting -> {
				val topF = getFeature(pos + Vec2.UP)
				val rightF = getFeature(pos + Vec2.RIGHT)
				val leftF = getFeature(pos + Vec2.LEFT)
				val downF = getFeature(pos + Vec2.DOWN)

				val topConnect = if (topF != null && topF.connectsTo(baseF, Direction.DOWN)) 1 else 0
				val rightConnect = if (rightF != null && rightF.connectsTo(baseF, Direction.LEFT)) 2 else 0
				val leftConnect = if (leftF != null && leftF.connectsTo(baseF, Direction.RIGHT)) 4 else 0
				val downConnect = if (downF != null && downF.connectsTo(baseF, Direction.UP)) 8 else 0

				val index = topConnect + rightConnect + leftConnect + downConnect
				return WorldSim.sprites[baseSprite.connections[index]]
			}
		}
	}

	/** Movement speed multiplier for moving from the tile at [pos].
	 * Thread safe. */
	fun getMovementSpeedMultiplier(pos:Vec2):Float {
		val feature = getFeature(pos) ?: return 1f
		val aspects = feature.aspects
		when {
			FeatureAspect.WALK_FASTER in aspects -> return 1.3f
			FeatureAspect.WALK_SLOWER in aspects -> return 0.75f
			FeatureAspect.WALK_MUCH_SLOWER in aspects -> return 0.5f
		}
		return 1f
	}

	/** Get feature at the tile at given [pos].
	 * Thread safe. */
	fun getFeature(pos:Vec2): Feature? {
		val chunk: Chunk = getChunk(pos) ?: return null
		return chunk.features[pos.tileKey]
	}

	/** Set feature for the given tile at given [pos].
	 * Thread safe **but prone to races**. */
	fun setFeature(pos:Vec2, feature: Feature?) {
		val chunk: Chunk = getChunk(pos) ?: return
		chunk.features[pos.tileKey] = feature
	}

	/** Get the amount of items at the given tile of given item type.
	 * Thread safe. */
	fun getItemCount(pos:Vec2, item: Item):Int {
		val chunk: Chunk = getChunk(pos) ?: return 0
		val items = chunk.items[pos.tileKey] ?: return 0
		return items[item.ordinal]
	}

	fun setItemCount(pos:Vec2, item:Item, count:Int):Boolean {
		val items = getChunk(pos)?.items ?: return false
		val tileKey = pos.tileKey
		var tileItems = items[tileKey]
		if (tileItems == null) {
			tileItems = IntArray(ITEMS.size)
			items[tileKey] = tileItems
		}
		tileItems[item.ordinal] = count
		return true
	}

	override fun update(delta: Float) {
		val chunksToPopulate = chunksToPopulate
		val engine = engine
		for (i in 0 until chunksToPopulate.size) {
			val chunkKey = chunksToPopulate.items[i]
			populator.populateChunk(engine, chunks[chunkKey]!!, Vec2(chunkKey))
		}
		chunksToPopulate.clear()
	}

	class Chunk {
		val tiles = Array(CHUNK_AREA) { Tile.WATER }
		val features = arrayOfNulls<Feature>(CHUNK_AREA)
		val items = arrayOfNulls<IntArray>(CHUNK_AREA)

		/** Cache with IDs of entities in this chunk */
		val entities = EntitySet()
	}
}

inline fun World.forChunksNear(pos:Vec2, distance:Int, action:(World.Chunk) -> Unit) {
	val centerX = pos.x
	val centerY = pos.y

	val firstChunkX = (centerX - distance) shr CHUNK_SIZE_SHIFT
	val lastChunkX = (centerX + distance) shr CHUNK_SIZE_SHIFT
	val firstChunkY = (centerY - distance) shr CHUNK_SIZE_SHIFT
	val lastChunkY = (centerY + distance) shr CHUNK_SIZE_SHIFT
	for (x in firstChunkX .. lastChunkX) {
		for (y in firstChunkY .. lastChunkY) {
			val chunk = getChunk(Vec2(x shl CHUNK_SIZE_SHIFT, y shl CHUNK_SIZE_SHIFT)) ?: continue
			action(chunk)
		}
	}
}

interface WorldGenerator {
	fun generateChunk(world: World, chunk: World.Chunk, chunkPos:Vec2)
}

interface ChunkPopulator {
	fun populateChunk(engine:Engine, chunk:World.Chunk, chunkPos:Vec2)
}
