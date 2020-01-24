package com.darkyen.worldSim

import com.badlogic.gdx.Input
import com.badlogic.gdx.math.MathUtils
import com.darkyen.worldSim.input.GameInput
import com.github.antag99.retinazer.Engine
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Implements simulation speed control and updates the [Engine] - taking care to not supply time-steps that are too large.
 */
class SimulationSpeedRegulator : WorldSimGame.InputProcessorProvider {

	private var baseMultiplier = 1f
		set(value) {
			field = MathUtils.clamp(value, 1f, 1024f)
		}

	val multiplier:Float
		get() = if (paused.isPressed) 0f else baseMultiplier

	var simulationDelta:Float = 0f
		private set

	private val faster = GameInput.function("Simulation faster", GameInput.Binding.bindKeyboard(Input.Keys.UP)).listen { times, pressed ->
		if (pressed) {
			baseMultiplier *= 2f
			true
		} else false
	}
	private val slower = GameInput.function("Simulation slower", GameInput.Binding.bindKeyboard(Input.Keys.DOWN)).listen { times, pressed ->
		if (pressed) {
			baseMultiplier /= 2f
			true
		} else false
	}
	private val paused = GameInput.toggleFunction("Pause simulation", GameInput.Binding.bindKeyboard(Input.Keys.P))

	override val inputProcessor = GameInput(faster, slower, paused)

	private val MAX_MS = 50

	fun updateForDelta(delta:Float, engine: Engine) {
		val fullSimulationDelta = delta * multiplier
		var simulateMs = (fullSimulationDelta * 1000).roundToInt()
		while (simulateMs > 0) {
			val step = min(simulateMs, MAX_MS)
			simulateMs -= step
			simulationDelta = step / 1000f
			engine.update()
		}
	}
}