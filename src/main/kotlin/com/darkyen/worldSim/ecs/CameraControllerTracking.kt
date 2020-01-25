package com.darkyen.worldSim.ecs

import com.badlogic.gdx.math.Rectangle
import com.github.antag99.retinazer.Component
import com.github.antag99.retinazer.Mapper
import com.github.antag99.retinazer.Wire
import com.github.antag99.retinazer.systems.EntityProcessorSystem

/**
 * Component on which camera focuses.
 */
class CameraTrackerC(var radiusVisibleAround:Float = 1f) : Component

class CameraTrackerS : EntityProcessorSystem(COMPONENT_DOMAIN.familyWith(PositionC::class.java, CameraTrackerC::class.java)) {
	@Wire
	private lateinit var positionMapper: Mapper<PositionC>
	@Wire
	private lateinit var cameraTrackerMapper: Mapper<CameraTrackerC>
	@Wire
	private lateinit var camera: CameraService

	private val thisFrameFraming = Rectangle()
	private val tmp = Rectangle()
	private var firstEntity = true

	override fun update() {
		firstEntity = true
		super.update()
		if (!firstEntity) {
			camera.lookAt.set(thisFrameFraming)
		}
	}

	override fun process(entity: Int) {
		val positionC = positionMapper[entity]!!
		val cameraTrackerC = cameraTrackerMapper[entity]!!

		val x = positionC.getX()
		val y = positionC.getY()
		val radiusVisibleAround = cameraTrackerC.radiusVisibleAround
		val thisEntityView = tmp.set(x - radiusVisibleAround, y - radiusVisibleAround, radiusVisibleAround + radiusVisibleAround, radiusVisibleAround + radiusVisibleAround)
		if (firstEntity) {
			thisFrameFraming.set(thisEntityView)
			firstEntity = false
		} else {
			thisFrameFraming.merge(thisEntityView)
		}
	}
}