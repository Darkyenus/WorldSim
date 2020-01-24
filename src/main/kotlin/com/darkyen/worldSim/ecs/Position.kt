package com.darkyen.worldSim.ecs

import com.badlogic.gdx.math.Vector2
import com.darkyen.worldSim.SimulationSpeedRegulator
import com.darkyen.worldSim.util.Direction
import com.darkyen.worldSim.util.Vec2
import com.github.antag99.retinazer.Component
import com.github.antag99.retinazer.Mapper
import com.github.antag99.retinazer.Wire
import com.github.antag99.retinazer.systems.EntityProcessorSystem
import kotlin.math.min

/** Position component. */
class PositionC : Component {
    var pos = Vec2(0, 0)
	var movement: Direction = Direction.NONE
	var progress = 0f
	/** Movement speed in tiles per second. */
	var speed = 1f

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

/** Position system. Moves [PositionC]omponents and updates [SpatialLookupService]s. */
class PositionS : EntityProcessorSystem(COMPONENT_DOMAIN.familyWith(PositionC::class.java, AgentC::class.java)) {

	@Wire
	private lateinit var positionMapper: Mapper<PositionC>
	@Wire
	private lateinit var agentS: AgentS
	@Wire
	private lateinit var simulationClock : SimulationSpeedRegulator
	@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
	@Wire
	private lateinit var spatialLookupServices : java.util.List<SpatialLookupService>

	override fun process(entity: Int) {
		val delta = simulationClock.simulationDelta
		val position = positionMapper[entity]!!
		val movement = position.movement
		if (movement == Direction.NONE) {
			return
		}

		val newProgress = position.progress + delta * position.speed
		if (newProgress < 1f) {
			position.progress = newProgress
			return
		}
		// Move tile!
		val oldChunkKey = position.pos.chunkKey
		position.pos += movement.vec
		position.progress = min(newProgress - 1f, 1f)
		position.movement = Direction.NONE
		agentS.continueEntity(entity)

		val newChunkKey = position.pos.chunkKey

		if (oldChunkKey != newChunkKey) {
			for (service in spatialLookupServices) {
				service.onEntityMovedAcrossChunks(entity)
			}
		}
	}
}