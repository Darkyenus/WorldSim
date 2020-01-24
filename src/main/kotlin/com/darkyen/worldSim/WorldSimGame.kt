package com.darkyen.worldSim

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.darkyen.worldSim.ecs.AgentDetailUI
import com.darkyen.worldSim.ecs.AgentNeedS
import com.darkyen.worldSim.ecs.AgentS
import com.darkyen.worldSim.ecs.AgentSpatialLookup
import com.darkyen.worldSim.ecs.AgentSpeechS
import com.darkyen.worldSim.ecs.COMPONENT_DOMAIN
import com.darkyen.worldSim.ecs.CameraControllerFree
import com.darkyen.worldSim.ecs.CameraService
import com.darkyen.worldSim.ecs.DecayS
import com.darkyen.worldSim.ecs.PathFinder
import com.darkyen.worldSim.ecs.PositionS
import com.darkyen.worldSim.ecs.RenderS
import com.darkyen.worldSim.ecs.RenderSpatialLookup
import com.darkyen.worldSim.ecs.SimulationSpeed
import com.darkyen.worldSim.ecs.World
import com.darkyen.worldSim.input.InputStack
import com.github.antag99.retinazer.Engine

/**
 *
 */
class WorldSimGame : Screen {
	private val engine = Engine(COMPONENT_DOMAIN,
			SimulationSpeed(),
			DecayS(),
			World(NoiseWorldGenerator(), EntityChunkPopulator),
			PathFinder(),
			AgentNeedS(),
			AgentS(),
			AgentSpeechS(),

			PositionS(),
			AgentSpatialLookup(),
			RenderSpatialLookup(),

			CameraControllerFree(),
			CameraService(),
			AgentDetailUI(),
			RenderS()
	)

	private val input = InputStack().apply {
		for (service in engine.getServices(InputProcessorProvider::class.java)) {
			push(service.inputProcessor, true)
		}
	}

	private val uiViewport = ScreenViewport()
	private val stage = Stage(uiViewport, WorldSim.batch).apply {
		for (service in engine.getServices(UILayerProvider::class.java)) {
			addActor(service.uiLayer)
		}
	}

	override fun show() {
		Gdx.input.inputProcessor = input
	}

	override fun hide() {}

	override fun render(delta: Float) {
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
		engine.update()
		stage.act(delta)
		stage.draw()
	}

	override fun pause() {}

	override fun resume() {}

	override fun resize(width: Int, height: Int) {
		uiViewport.update(width, height, true)
	}

	override fun dispose() {
		for (disposable in engine.getServices(Disposable::class.java)) {
			disposable.dispose()
		}
	}

	interface UILayerProvider {
		val uiLayer: Actor
	}

	interface InputProcessorProvider {
		val inputProcessor:InputProcessor
	}
}