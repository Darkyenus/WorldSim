package com.darkyen.worldSim

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.RandomXS128
import com.darkyen.worldSim.NoiseWorldGenerator.Biome.*
import com.darkyen.worldSim.ecs.CHUNK_SIZE
import com.darkyen.worldSim.ecs.WORLD_SIZE
import com.darkyen.worldSim.ecs.World
import com.darkyen.worldSim.ecs.WorldGenerator
import com.darkyen.worldSim.ecs.tileKey
import com.darkyen.worldSim.util.PerlinNoise
import com.darkyen.worldSim.util.Vec2
import com.darkyen.worldSim.util.Weighted
import com.darkyen.worldSim.util.WeightedSampler
import kotlin.math.abs
import kotlin.math.min

/**
 *
 */
class NoiseWorldGenerator(seed:Long = System.currentTimeMillis()) : WorldGenerator {

	private val terrainNoise = PerlinNoise(seed)
	private val terrainEdgeSize = 100f

	private fun terrainHeight(x:Float, y:Float):Float {
		val distanceToEdge = min(min(abs(x), abs(y)), min(abs(x - WORLD_SIZE), abs(y - WORLD_SIZE)))
		val bias = if (distanceToEdge < terrainEdgeSize) {
			1f - (distanceToEdge / terrainEdgeSize)
		} else 0f

		return terrainNoise.sampleDense(x, y) - bias
	}

	private val temperatureNoise = PerlinNoise(seed + 1L)
	private fun terrainTemperature(x:Float, y:Float, height:Float):Float {
		val height2 = height * height
		val height4 = height2 * height2
		val height8 = height4 * height4
		return temperatureNoise.sampleDense(x, y) * (1f - height8)
	}

	private val precipitationNoise = PerlinNoise(seed + 2L)
	private fun terrainPrecipitation(x:Float, y:Float, height:Float, temperature:Float):Float {
		val min = 0f
		val maxByHeight = 1f - height * height
		val max = (maxByHeight + temperature) * 0.5f
		return MathUtils.lerp(precipitationNoise.sampleDense(x, y), min, max)
	}

	private fun tile(height: Float, biome: Biome):Tile {
		return when {
			height < 0.4f -> {
				Tile.WATER
			}
			height > 0.8f -> {
				Tile.ROCK
			}
			else -> {
				when (biome) {
					TROPICAL_RAINFOREST, TROPICAL_SEASONAL_FOREST, TEMPERATE_SEASONAL_FOREST, TEMPERATE_RAINFOREST -> Tile.FOREST
					SUBTROPICAL_DESERT, SAVANNA -> Tile.DESERT
					TEMPERATE_GRASSLAND, WOODLAND, SHRUBLAND -> Tile.GRASS
					TUNDRA, BOREAL_FOREST, COLD_DESERT -> Tile.SNOW
				}
			}
		}
	}

	enum class Biome(vararg items: Weighted<Feature?>) {
		TROPICAL_RAINFOREST(Weighted(Feature.DECIDUOUS_FRUIT_FOREST, 1f), Weighted(Feature.DECIDUOUS_FOREST, 4f), Weighted(null, 8f)),
		TROPICAL_SEASONAL_FOREST(Weighted(Feature.DECIDUOUS_FRUIT_FOREST, 1f), Weighted(Feature.DECIDUOUS_FOREST, 4f), Weighted(null, 4f)),
		SAVANNA(Weighted(Feature.DECIDUOUS_FOREST, 1f), Weighted(Feature.BERRY_BUSHES, 1f), Weighted(Feature.BUSHES, 4f), Weighted(null, 50f)),
		SUBTROPICAL_DESERT(Weighted(Feature.BUSHES, 1f), Weighted(null, 100f)),
		TEMPERATE_GRASSLAND(Weighted(Feature.DECIDUOUS_FOREST, 1f), Weighted(Feature.BUSHES, 2f), Weighted(null, 10f)),
		COLD_DESERT(Weighted(Feature.BUSHES, 1f), Weighted(null, 30f)),
		WOODLAND(Weighted(Feature.DECIDUOUS_FOREST, 5f), Weighted(Feature.DECIDUOUS_FRUIT_FOREST, 1f), Weighted(null, 15f)),
		SHRUBLAND(Weighted(Feature.BERRY_BUSHES, 1f), Weighted(Feature.BUSHES, 2f), Weighted(null, 20f)),
		TEMPERATE_SEASONAL_FOREST(Weighted(Feature.DECIDUOUS_FOREST_DEEP, 3f), Weighted(Feature.DECIDUOUS_FOREST, 3f), Weighted(Feature.DECIDUOUS_FRUIT_FOREST, 0.5f), Weighted(null, 6f)),
		TEMPERATE_RAINFOREST(Weighted(Feature.DECIDUOUS_FOREST_DEEP, 3f), Weighted(Feature.DECIDUOUS_FOREST, 2f), Weighted(Feature.DECIDUOUS_FRUIT_FOREST, 0.8f), Weighted(null, 2f)),
		BOREAL_FOREST(Weighted(Feature.CONIFEROUS_FOREST_DEEP, 5f), Weighted(Feature.CONIFEROUS_FOREST, 1f), Weighted(null, 4f)),
		TUNDRA(Weighted(Feature.CONIFEROUS_FOREST_DEEP, 3f), Weighted(Feature.CONIFEROUS_FOREST, 3f), Weighted(null, 20f));

		private val sampler = WeightedSampler(*items)

		fun randomFeature(seed:Int):Feature? {
			val random = RANDOM
			random.setSeed(seed.toLong())
			return sampler.sample(random)
		}

		private companion object {
			val RANDOM = RandomXS128()
		}
	}

	// Based on https://en.wikipedia.org/wiki/File:Climate_influence_on_terrestrial_biome.svg
	private fun biome(height:Float, temperature:Float, precipitation:Float):Biome {
		return if (temperature > 0.75f) {
			if (precipitation > 0.66f) {
				// Tropical rainforest
				TROPICAL_RAINFOREST
			} else if (precipitation > 0.33f) {
				// Tropical seasonal forest/savanna
				if (height < 0.4f) {
					TROPICAL_SEASONAL_FOREST
				} else {
					SAVANNA
				}
			} else {
				// Subtropical desert
				SUBTROPICAL_DESERT
			}
		} else if (temperature > 0.4f) {
			if (precipitation < 0.1f) {
				// Temperate grassland/cold desert
				if (height < 0.35f) {
					TEMPERATE_GRASSLAND
				} else {
					COLD_DESERT
				}
			} else if (precipitation < 0.2f) {
				// Woodland/shrubland
				if (height < 0.35f) {
					WOODLAND
				} else {
					SHRUBLAND
				}
			} else if (precipitation < 0.4f) {
				// Temperate/seasonal forest
				TEMPERATE_SEASONAL_FOREST
			} else {
				// Temperate rainforest
				TEMPERATE_RAINFOREST
			}
		} else if (temperature > 0.2f) {
			if (precipitation < 0.075f) {
				// Temperate grassland/cold desert
				if (height < 0.35f) {
					TEMPERATE_GRASSLAND
				} else {
					COLD_DESERT
				}
			} else if (precipitation < 0.15f) {
				// Woodland/shrubland
				if (height < 0.35f) {
					WOODLAND
				} else {
					SHRUBLAND
				}
			} else {
				// Boreal forest
				BOREAL_FOREST
			}
		} else {
			// Tundra
			TUNDRA
		}
	}

	private fun feature(seed:Int, biome:Biome):Feature? {
		return biome.randomFeature(seed)
	}

	override fun generateChunk(world: World, chunk: World.Chunk, chunkPos: Vec2) {
		val chunkXOff = chunkPos.x
		val chunkYOff = chunkPos.y
		for (x in 0 until CHUNK_SIZE) {
			for (y in 0 until CHUNK_SIZE) {
				val worldXI = chunkXOff + x
				val worldYI = chunkYOff + y
				val tileKey = Vec2(worldXI, worldYI).tileKey
				val worldX = worldXI.toFloat()
				val worldY = worldYI.toFloat()

				val height = terrainHeight(worldX, worldY)
				val temperature = terrainTemperature(worldX, worldY, height)
				val precipitation = terrainPrecipitation(worldX, worldY, height, temperature)

				val biome = biome(height, temperature, precipitation)
				val tile = tile(height, biome)

				val feature = if (tile.type == TileType.LAND) {
					val seed = hash(worldX, worldY)
					feature(seed, biome)
				} else null

				chunk.tiles[tileKey] = tile
				chunk.features[tileKey] = feature
			}
		}
	}

	private fun hash(x: Float, y:Float): Int {
		var h = (java.lang.Float.floatToRawIntBits(x).toLong() shl 32) or (java.lang.Float.floatToRawIntBits(y).toLong() and 0xFFFFFFFFL)
		h = h xor (h ushr 33)
		h *= -0xae502812aa7333L
		h = h xor (h ushr 33)
		h *= -0x3b314601e57a13adL
		h = h xor (h ushr 33)
		return h.toInt()
	}
}