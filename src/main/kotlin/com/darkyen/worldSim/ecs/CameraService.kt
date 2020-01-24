package com.darkyen.worldSim.ecs

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.github.antag99.retinazer.EngineService

/**
 *
 */
class CameraService : EngineService {

	val viewport = ExtendViewport(1f, 1f).apply { //arbitrary
		val camera = camera
		camera.direction[0f, 0f] = -1f
		camera.up[0f, 1f] = 0f
		camera.near = 0.5f
		camera.far = 1.5f
	}

	val lookAt = Rectangle()

	override fun update(delta: Float) {
		val v = viewport
		val lookAt = lookAt
		val position = v.camera.position
		val w = Gdx.graphics.width
		val h = Gdx.graphics.height

		v.minWorldWidth = lookAt.width
		v.minWorldHeight = lookAt.height
		position.x = lookAt.x + lookAt.width / 2
		position.y = lookAt.y + lookAt.height / 2
		position.z = 1f
		v.update(w, h, false)
	}

	private val unproject_tmp = Vector2()

	fun unproject(screenX:Int, screenY:Int): Vector2 {
		return viewport.unproject(unproject_tmp.set(screenX.toFloat(), screenY.toFloat()))
	}

	fun unproject(screenX:Int, screenY:Int, out:Vector2): Vector2 {
		return viewport.unproject(out.set(screenX.toFloat(), screenY.toFloat()))
	}
}