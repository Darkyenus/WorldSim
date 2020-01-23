package com.darkyen.worldSim

import com.darkyen.worldSim.ai.AIContext
import com.darkyen.worldSim.ai.brain
import com.darkyen.worldSim.ecs.AGENT_ATTRIBUTES
import com.darkyen.worldSim.ecs.AgentC
import com.darkyen.worldSim.ecs.ChunkPopulator
import com.darkyen.worldSim.ecs.MATURITY_AGE_YEAR
import com.darkyen.worldSim.ecs.PositionC
import com.darkyen.worldSim.ecs.RenderC
import com.darkyen.worldSim.ecs.World
import com.darkyen.worldSim.ecs.atTile
import com.darkyen.worldSim.util.Vec2
import com.github.antag99.retinazer.Engine
import kotlin.math.max
import kotlin.random.Random

/**
 *
 */
object EntityChunkPopulator : ChunkPopulator {

	val maleSprites = intArrayOf(
		39, 44, 45, 47, 40, 50, 70, 71, 52, 63, 72, 68, 53, 62, 73, 49, 61, 55
	)

	val femaleSprites = intArrayOf(
		82, 41, 42, 43, 65, 70, 46, 51, 64, 71, 67, 52, 72, 62, 54, 74, 66, 60, 75
	)

	val childSprites = intArrayOf(
			48, 56, 59, 76, 69, 57, 58, 77
	)

	override fun populateChunk(engine: Engine, chunk: World.Chunk, chunkPos: Vec2) {
		val positionC = engine.getMapper(PositionC::class.java)
		val agentC = engine.getMapper(AgentC::class.java)
		val renderC = engine.getMapper(RenderC::class.java)

		for (i in 0 until 50) {
			val tileKey = chunk.tiles.indices.random()
			if (chunk.tiles[tileKey].type != TileType.LAND) {
				continue
			}

			val worldPos = chunkPos.atTile(tileKey)
			val entity = engine.createEntity()

			val genderMale = Random.nextBoolean()
			val age = Random.nextInt(0, 70)

			positionC.create(entity).pos = worldPos
			agentC.add(entity, AgentC(AIContext::brain, genderMale).also {
				for (attribute in AGENT_ATTRIBUTES) {
					it.attributes[attribute.ordinal] = Random.nextInt(max(attribute.min.toInt(), 0), attribute.max + 1).toByte()
				}
			})
			renderC.create(entity).sprite = when {
				age < MATURITY_AGE_YEAR -> childSprites
				genderMale -> maleSprites
				else -> femaleSprites
			}.random()
		}
	}

}