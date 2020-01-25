package com.darkyen.worldSim.ecs

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.darkyen.worldSim.RenderService
import com.darkyen.worldSim.SimulationSpeedRegulator
import com.darkyen.worldSim.WorldSim
import com.darkyen.worldSim.WorldSimGame
import com.darkyen.worldSim.input.GameInput
import com.darkyen.worldSim.util.WHITE_BITS
import com.darkyen.worldSim.util.render
import com.darkyen.worldSim.util.renderTile
import com.darkyen.worldSim.util.GrapherPane
import com.darkyen.worldSim.util.GrapherPane.GraphData
import com.darkyen.worldSim.util.Text
import com.darkyen.worldSim.util.Vec2
import com.github.antag99.retinazer.Component
import com.github.antag99.retinazer.EntitySystem
import com.github.antag99.retinazer.Mapper
import com.github.antag99.retinazer.Wire

/** This entity is rendered. */
class RenderC : Component {
	var sprite:Int = 0
}

private val RENDER_FAMILY = COMPONENT_DOMAIN.familyWith(PositionC::class.java, RenderC::class.java)

class RenderSpatialLookup : SpatialLookupService(RENDER_FAMILY)

class RenderS : EntitySystem(RENDER_FAMILY), RenderService, WorldSimGame.UILayerProvider, WorldSimGame.InputProcessorProvider {

	private var debugDrawEnabled = false
		set(value) {
			uiLayer.isVisible = value
			field = value
		}


	@Wire
	private lateinit var world: World
	@Wire
	private lateinit var simulationClock : SimulationSpeedRegulator
	@Wire
	private lateinit var position: Mapper<PositionC>
	@Wire
	private lateinit var agent: Mapper<AgentC>
	@Wire
	private lateinit var render: Mapper<RenderC>
	@Wire
	private lateinit var camera: CameraService
	@Wire
	private lateinit var renderSpatialLookup: RenderSpatialLookup

	companion object {
		private const val OVERLAP = 5f
	}

	private val tmp1 = Vector2()
	private val tmp2 = Vector2()
	private val debugDrawText = Text()
	private val frustum = Rectangle()

	override fun renderUpdate(delta: Float) {
		if (debugDrawEnabled) {
			updateDebugDrawing()
		}

		val v = camera.viewport


		val batch = WorldSim.batch
		batch.projectionMatrix = v.camera.combined
		batch.packedColor = WHITE_BITS
		batch.begin()
		val start = v.unproject(tmp1.set(v.screenX.toFloat(), (v.screenY + v.screenHeight).toFloat()))
		val end = v.unproject(tmp2.set((v.screenX + v.screenWidth).toFloat(), v.screenY.toFloat()))

		start.add(-OVERLAP, -OVERLAP)
		end.add(OVERLAP, OVERLAP)
		val frustum = frustum.set(start.x, start.y, end.x - start.x, end.y - start.y)
		draw(batch, Vec2(MathUtils.floor(start.x), MathUtils.floor(start.y)), Vec2(MathUtils.ceil(end.x), MathUtils.ceil(end.y)), frustum)

		batch.end()
	}

	private fun draw(b: Batch, low:Vec2, high:Vec2, frustum: Rectangle) {
		val world = world

		//Draw tiles & features
		for (y in high.y downTo low.y) {
			for (x in low.x..high.x) {
				val xy = Vec2(x, y)
				world.getTileSprite(xy).renderTile(b, x, y)

				world.getFeatureSprite(xy)?.render(b, x.toFloat(), y.toFloat(), 1f, 1f)
			}
		}

		val posTmp = Vector2()

		// Draw entities
		renderSpatialLookup.forEntitiesInChunksSpanningRectangle(low, high) { entity ->
			val position = position[entity]!!
			val pos = position.getPosition(posTmp)
			if (!frustum.contains(pos)) return@forEntitiesInChunksSpanningRectangle //Frustum culling
			val sprite = WorldSim.sprites[render[entity]!!.sprite]

			val activity = agent[entity]?.activity ?: AgentActivity.IDLE

			if (activity == AgentActivity.SLEEPING) {
				val rotation = if ((entity and 1) == 1) 90f else -90f
				sprite.render(b, pos.x, pos.y, 1f, 1f, rotation)
			} else {
				sprite.render(b, pos.x, pos.y, 1f, 1f)
			}


			// Render activity info, if available
			val spriteId = activity.sprite
			if (spriteId != -1) {
				WorldSim.sprites[spriteId].render(b, pos.x, pos.y, 1f, 1f)
			}
		}
	}

	private val stepTimeData = GraphData(256, Color.RED, 0f, 0.1f, false)
	private val memData = GraphData(256, Color.GREEN, 0f, 20000f, true)
	private val entityCount = GraphData(256, Color.YELLOW, 0f, 1000f, true)

	private val grapherPane = GrapherPane().apply {
		graphs.add(stepTimeData)
		graphs.add(memData)
		graphs.add(entityCount)
	}

	private val debugTextLabel = Label("", Label.LabelStyle(WorldSim.debugFont, Color.WHITE))

	private fun updateDebugDrawing() {
		stepTimeData.addDataPoint(Gdx.graphics.rawDeltaTime)
		val rt = Runtime.getRuntime()
		memData.addDataPoint((rt.totalMemory() - rt.freeMemory()) / 1000f)
		entityCount.addDataPoint(engine.entities.size().toFloat())

		val cursorPos = camera.unproject(Gdx.input.x, Gdx.input.y)

		val text = debugDrawText
		text.clear()
		text.append("X: ").append(cursorPos.x, 2).append(" Y: ").append(cursorPos.y, 2)
		text.append("\nFPS: ").append(Gdx.graphics.framesPerSecond).append('\n').append("Speed: ").append(simulationClock.multiplier, 0)
		debugTextLabel.setText(text)
	}

	override val uiLayer: Table = Table().apply {
		setFillParent(true)
		pad(10f).align(Align.top)
		add(debugTextLabel).align(Align.topLeft)
		add().expandX()
		add(grapherPane).minSize(200f, 100f).padRight(50f).align(Align.topRight)

		isVisible = debugDrawEnabled
	}

	private val debugDrawToggle = GameInput.toggleFunction("Toggle Debug Draw", GameInput.Binding.bindKeyboard(Input.Keys.F3)).listen { _: Int, pressed: Boolean ->
		debugDrawEnabled = pressed
		true
	}

	override val inputProcessor:GameInput = GameInput(debugDrawToggle)
}