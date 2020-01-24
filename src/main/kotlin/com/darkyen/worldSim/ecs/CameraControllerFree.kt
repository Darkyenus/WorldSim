package com.darkyen.worldSim.ecs

import com.badlogic.gdx.Input
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.darkyen.worldSim.WorldSimGame
import com.darkyen.worldSim.input.GameInput
import com.github.antag99.retinazer.EngineService
import com.github.antag99.retinazer.Wire

/**
 *
 */
class CameraControllerFree : EngineService, WorldSimGame.InputProcessorProvider {

	private val upInput = GameInput.function("Move Up", GameInput.Binding.bindKeyboard(Input.Keys.W))
	private val downInput = GameInput.function("Move Down", GameInput.Binding.bindKeyboard(Input.Keys.S))
	private val leftInput = GameInput.function("Move Left", GameInput.Binding.bindKeyboard(Input.Keys.A))
	private val rightInput = GameInput.function("Move Right", GameInput.Binding.bindKeyboard(Input.Keys.D))

	private val zoomInInput = GameInput.function("Zoom In", GameInput.Binding.bindScrollWheel(true)).listen { times, pressed ->
		val oldZoomLevel = zoomLevel
		if (pressed) {
			zoomLevel = MathUtils.clamp(zoomLevel + times, minZoomLevel, maxZoomLevel)
		}
		oldZoomLevel != zoomLevel
	}
	private val zoomOutInput = GameInput.function("Zoom Out", GameInput.Binding.bindScrollWheel(false)).listen { times, pressed ->
		val oldZoomLevel = zoomLevel
		if (pressed) {
			zoomLevel = MathUtils.clamp(zoomLevel - times, minZoomLevel, maxZoomLevel)
		}
		oldZoomLevel != zoomLevel
	}

	override val inputProcessor = GameInput(upInput, downInput, leftInput, rightInput, zoomInInput, zoomOutInput)

	@Wire
	private lateinit var camera:CameraService

	private val movement = Vector2()
	private val lookAt = Vector2()

	private val minZoomLevel = 10
	private val maxZoomLevel = WORLD_SIZE
	private var zoomLevel = 20

	override fun update(delta: Float) {
		val movement = movement.setZero()
		val speed = zoomLevel / 3f
		if (leftInput.isPressed) {
			movement.x -= 1f
		} else if (rightInput.isPressed) {
			movement.x += 1f
		}
		if (upInput.isPressed) {
			movement.y += 1f
		} else if (downInput.isPressed) {
			movement.y -= 1f
		}
		if (movement.x != 0f || movement.y != 0f) {
			movement.nor().scl(speed * delta)

			lookAt.add(movement)
		}

		val lookAtSize = zoomLevel.toFloat()
		camera.lookAt.set(lookAt.x - lookAtSize / 2f, lookAt.y - lookAtSize / 2f, lookAtSize, lookAtSize)
	}
}