package com.darkyen.worldSim.ecs

import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.LongMap
import com.darkyen.worldSim.Feature
import com.darkyen.worldSim.Sprite
import com.darkyen.worldSim.Tile
import com.darkyen.worldSim.WorldSim
import com.darkyen.worldSim.util.Side
import com.github.antag99.retinazer.EngineService
import com.github.antag99.retinazer.EntitySet

private const val COORD_MASK:Long = 0x7FFF_FFFFL
private const val PACKED_MASK:Long = 0x7FFF_FFFF_7FFF_FFFFL
/** Garbage-less 2D integer vector. Supports 31-bit precision on each axis. */
inline class Vec2(val packed:Long) {

	constructor(x:Int, y:Int):this(((x.toLong() and COORD_MASK) shl 32) or (y.toLong() and COORD_MASK))

	val x:Int
		get() = ((packed shl 1) shr 33).toInt()

	val y:Int
		get() = ((packed shl 33) shr 33).toInt()

	operator fun plus(other:Vec2):Vec2 {
		return Vec2((packed + other.packed) and PACKED_MASK)
	}

	operator fun unaryMinus():Vec2 {
		return Vec2(((packed xor PACKED_MASK) + 0x0000_0001_0000_0001L) and PACKED_MASK)
	}

	operator fun minus(other:Vec2):Vec2 {
		return Vec2((packed + (other.packed xor PACKED_MASK) + 0x0000_0001_0000_0001L) and PACKED_MASK)
	}

	operator fun times(factor:Int):Vec2 {
		return Vec2(x * factor, y * factor)
	}

	operator fun div(factor:Int):Vec2 {
		return Vec2(x / factor, y / factor)
	}

	val len2:Int
		get() {
			val x = this.x
			val y = this.y
			return x*x + y*y
		}

	val len:Float
		get() = kotlin.math.sqrt(len2.toFloat())

	companion object {
		val UP = Vec2(0, 1)
		val DOWN = Vec2(0, -1)
		val LEFT = Vec2(-1, 0)
		val RIGHT = Vec2(1, 0)
	}
}

private const val CHUNK_SIZE_SHIFT = 5
const val CHUNK_SIZE = 1 shl CHUNK_SIZE_SHIFT
private const val CHUNK_AREA = CHUNK_SIZE * CHUNK_SIZE
private const val CHUNK_SIZE_MASK = CHUNK_SIZE - 1
private const val CHUNK_KEY_MASK:Long = ((CHUNK_SIZE_MASK.toLong()) or (CHUNK_SIZE_MASK.toLong() shl 32)).inv()

private const val WORLD_SIZE_SHIFT = 9
const val WORLD_SIZE = 1 shl WORLD_SIZE_SHIFT
private const val WORLD_SIZE_MASK = WORLD_SIZE - 1
private const val WORLD_SIZE_RANGE_CHECK_MASK:Long = (WORLD_SIZE_MASK.toLong()) or (WORLD_SIZE_MASK.toLong() shl 32)

val Vec2.chunkKey:Long
	get() = packed and CHUNK_KEY_MASK

val Vec2.chunkX:Int
	get() = x shr CHUNK_SIZE_SHIFT

val Vec2.chunkY:Int
	get() = y shr CHUNK_SIZE_SHIFT

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



/**
 *
 */
class World(
		/** Used to generate new chunks */
		private val generator: WorldGenerator
) : EngineService {

	private val chunks = LongMap<Chunk>()
	var debugGeneratedChunks = 0

	/**
	 * Returns chunk at requested coordinates.
	 * When no chunk exists, one is created.
	 */
	fun getChunk(pos:Vec2): Chunk? {
		return getChunk(pos.chunkKey)
	}

	/**
	 * Returns chunk at requested chunk key.
	 * When no chunk exists, one is created.
	 */
	fun getChunk(chunkKey:Long):Chunk? {
		if ((chunkKey and WORLD_SIZE_RANGE_CHECK_MASK) != chunkKey) {
			return null
		}

		var result = chunks[chunkKey]
		if (result == null) {
			result = Chunk()
			generator.generateChunk(this, result, Vec2(chunkKey))
			chunks.put(chunkKey, result)
			debugGeneratedChunks += 1
		}
		return result
	}

	fun getTile(pos:Vec2): Tile {
		val chunk: Chunk = getChunk(pos) ?: return Tile.WATER
		return chunk.tiles[pos.tileKey]
	}

	fun setTile(pos:Vec2, tile: Tile) {
		val chunk: Chunk = getChunk(pos) ?: return
		chunk.tiles[pos.tileKey] = tile
	}

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

				val topConnect = if (topF != null && topF.connectsTo(baseF, Side.BOTTOM)) 1 else 0
				val rightConnect = if (rightF != null && rightF.connectsTo(baseF, Side.LEFT)) 2 else 0
				val leftConnect = if (leftF != null && leftF.connectsTo(baseF, Side.RIGHT)) 4 else 0
				val downConnect = if (downF != null && downF.connectsTo(baseF, Side.TOP)) 8 else 0

				val index = topConnect + rightConnect + leftConnect + downConnect
				return WorldSim.sprites[baseSprite.connections[index]]
			}
		}
	}

	fun getFeature(pos:Vec2): Feature? {
		val chunk: Chunk = getChunk(pos) ?: return null
		return chunk.features[pos.tileKey]
	}

	fun setFeature(pos:Vec2, feature: Feature?) {
		val chunk: Chunk = getChunk(pos) ?: return
		chunk.features[pos.tileKey] = feature
	}

	class Chunk {
		val tiles = Array(CHUNK_AREA) { Tile.WATER }
		val features = arrayOfNulls<Feature>(CHUNK_AREA)

		/** Cache with IDs of entities in this chunk */
		val entities = EntitySet()
	}
}

interface WorldGenerator {
	fun generateChunk(world: World, chunk: World.Chunk, chunkPos:Vec2)
}
