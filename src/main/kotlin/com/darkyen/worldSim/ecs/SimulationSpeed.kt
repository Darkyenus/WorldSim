package com.darkyen.worldSim.ecs

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.MathUtils
import com.darkyen.worldSim.WorldSimGame
import com.darkyen.worldSim.input.GameInput
import com.github.antag99.retinazer.EngineService

/**
 *
 */
class SimulationSpeed : EngineService, WorldSimGame.InputProcessorProvider {

	private var baseMultiplier = 1f
		set(value) {
			field = MathUtils.clamp(value, 1f, 100f)
		}

	val multiplier:Float
		get() = if (paused.isPressed) 0f else baseMultiplier

	var simulationDelta:Float = 0f
		private set

	private val faster = GameInput.function("Simulation faster", GameInput.Binding.bindKeyboard(Input.Keys.UP)).listen { times, pressed ->
		if (pressed) {
			baseMultiplier += times
			true
		} else false
	}
	private val slower = GameInput.function("Simulation slower", GameInput.Binding.bindKeyboard(Input.Keys.DOWN)).listen { times, pressed ->
		if (pressed) {
			baseMultiplier -= times
			true
		} else false
	}
	private val paused = GameInput.toggleFunction("Pause simulation", GameInput.Binding.bindKeyboard(Input.Keys.P))

	override val inputProcessor = GameInput(faster, slower, paused)

	override fun update() {
		val delta = Gdx.graphics.deltaTime
		simulationDelta = delta * multiplier
	}
}