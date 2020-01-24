package com.darkyen.worldSim.ecs

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.HdpiUtils
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.darkyen.worldSim.WorldSim
import com.darkyen.worldSim.WorldSimGame
import com.darkyen.worldSim.input.GameInput
import com.darkyen.worldSim.render.WHITE_BITS
import com.darkyen.worldSim.render.render
import com.darkyen.worldSim.render.renderTile
import com.darkyen.worldSim.ui.debug.GrapherPane
import com.darkyen.worldSim.ui.debug.GrapherPane.GraphData
import com.darkyen.worldSim.util.Text
import com.darkyen.worldSim.util.Vec2
import com.darkyen.worldSim.util.forEach
import com.github.antag99.retinazer.Component
import com.github.antag99.retinazer.EntitySystem
import com.github.antag99.retinazer.Mapper
import com.github.antag99.retinazer.Wire
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** This entity is rendered. */
class RenderC : Component {
	var sprite:Int = 0
}

class RenderS(var pixelsPerUnit: Int = 0) : EntitySystem(COMPONENT_DOMAIN.familyWith(PositionC::class.java, RenderC::class.java)), WorldSimGame.UILayerProvider, WorldSimGame.InputProcessorProvider {

	private var debugDrawEnabled = false
		set(value) {
			uiLayer.isVisible = value
			field = value
		}

	private val viewport = ExtendViewport(1f, 1f).apply { //arbitrary
		val camera = camera
		camera.direction[0f, 0f] = -1f
		camera.up[0f, 1f] = 0f
		camera.near = 0.5f
		camera.far = 1.5f
	}

	val lookAt = Rectangle()

	private val frameBufferViewport = ScreenViewport(OrthographicCamera().apply {
		setToOrtho(true)
	})
	private var pixelFrameBuffer: FrameBuffer? = null

	@Wire
	private lateinit var world: World
	@Wire
	private lateinit var position: Mapper<PositionC>
	@Wire
	private lateinit var agent: Mapper<AgentC>
	@Wire
	private lateinit var render: Mapper<RenderC>

	companion object {
		var DEBUG_DRAW_INFO: CharSequence? = null

		private const val OVERLAP = 5f
	}

	private val tmp1 = Vector2()
	private val tmp2 = Vector2()
	private val debugDrawText = Text()
	private val frustum = Rectangle()


	private fun align(c: Float): Float {
		val k = pixelsPerUnit.toFloat()
		return (c * k).roundToInt() / k
	}

	override fun update(delta: Float) {
		if (debugDrawEnabled) {
			updateDebugDrawing()
		}

		val v = viewport
		val lookAt = lookAt
		val position = v.camera.position
		val w = Gdx.graphics.width
		val h = Gdx.graphics.height
		val bw = Gdx.graphics.backBufferWidth
		val bh = Gdx.graphics.backBufferHeight
		val frameBufferAreaWidth: Int
		val frameBufferAreaHeight: Int
		val frameBufferDrawWidth: Int
		val frameBufferDrawHeight: Int
		val frameBuffer: FrameBuffer?
		if (pixelsPerUnit != 0) { //Complex with framebuffer
			//How big pixels should be?
			val minPixelsW = (lookAt.width * pixelsPerUnit).toInt()
			val minPixelsH = (lookAt.height * pixelsPerUnit).toInt()
			if (minPixelsW == 0 || minPixelsH == 0) return
			val virtualPixelSize = max(min(bw / minPixelsW, bh / minPixelsH), 1)
			position.x = align(lookAt.x + lookAt.width / 2)
			position.y = align(lookAt.y + lookAt.height / 2)
			position.z = 1f
			//Round up to nearest even number, helps with visual glitches for some reason
			val fbWidth = (bw / virtualPixelSize + 1) and 1.inv()
			val fbHeight = (bh / virtualPixelSize + 1) and 1.inv()
			val minUnitsWidth = fbWidth / pixelsPerUnit.toFloat()
			val minUnitsHeight = fbHeight / pixelsPerUnit.toFloat()
			v.minWorldWidth = minUnitsWidth
			v.minWorldHeight = minUnitsHeight
			v.update(fbWidth, fbHeight, false)
			Gdx.gl.glViewport(v.screenX, v.screenY, v.screenWidth, v.screenHeight) //Override v.update viewport
			frameBufferAreaWidth = fbWidth
			frameBufferAreaHeight = fbHeight
			frameBufferDrawWidth = HdpiUtils.toLogicalX(fbWidth * virtualPixelSize)
			frameBufferDrawHeight = HdpiUtils.toLogicalY(fbHeight * virtualPixelSize)
			val existing = pixelFrameBuffer
			if (existing != null && existing.width >= fbWidth && existing.height >= fbHeight) {
				frameBuffer = existing
			} else {
				existing?.dispose()
				if (fbWidth <= 1 || fbHeight <= 1 || fbWidth > 8000 || fbHeight > 8000) return
				frameBuffer = FrameBuffer(Pixmap.Format.RGB888, fbWidth, fbHeight, false, false)
				pixelFrameBuffer = frameBuffer
			}
			frameBuffer.bind()
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
		} else { //Simple
			v.minWorldWidth = lookAt.width
			v.minWorldHeight = lookAt.height
			position.x = lookAt.x + lookAt.width / 2
			position.y = lookAt.y + lookAt.height / 2
			position.z = 1f
			v.update(w, h, false)
			frameBuffer = null
			frameBufferAreaHeight = 0
			frameBufferAreaWidth = frameBufferAreaHeight
			frameBufferDrawHeight = 0
			frameBufferDrawWidth = frameBufferDrawHeight
		}
		val batch = WorldSim.batch
		batch.projectionMatrix = v.camera.combined
		batch.packedColor = WHITE_BITS
		batch.begin()
		val start = v.unproject(tmp1.set(0f, h.toFloat()))
		val end = v.unproject(tmp2.set(w.toFloat(), 0f))

		start.add(-OVERLAP, -OVERLAP)
		end.add(OVERLAP, OVERLAP)
		val frustum = frustum.set(start.x, start.y, end.x - start.x, end.y - start.y)
		draw(batch, Vec2(MathUtils.floor(start.x), MathUtils.floor(start.y)), Vec2(MathUtils.ceil(end.x), MathUtils.ceil(end.y)), frustum)

		batch.end()
		if (frameBuffer != null) {
			FrameBuffer.unbind()
			val fbv = frameBufferViewport
			fbv.update(w, h, true)
			batch.projectionMatrix = fbv.camera.combined
			batch.packedColor = WHITE_BITS
			batch.begin()
			val colorBufferTexture = frameBuffer.colorBufferTexture
			colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Nearest)
			batch.draw(colorBufferTexture, 0f, 0f, frameBufferDrawWidth.toFloat(), frameBufferDrawHeight.toFloat(), 0, 0, frameBufferAreaWidth, frameBufferAreaHeight, false, false)
			batch.end()
		}
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
		for (chunkY in high.chunkY downTo low.chunkY) {
			for (chunkX in low.chunkX..high.chunkX) {
				val chunkCornerPos = Vec2.ofChunkCorner(chunkX, chunkY)
				val chunk = world.getChunk(chunkCornerPos) ?: continue
				chunk.entities.indices.forEach { entity ->
					val position = position[entity]
					val pos = position.getPosition(posTmp)
					if (!frustum.contains(pos)) return@forEach //Frustum culling
					val sprite = WorldSim.sprites[render[entity].sprite]

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

		val text = debugDrawText
		text.clear()
		text.append("X: ").append(lookAt.x + lookAt.width/2f, 2).append(" Y: ").append(lookAt.y + lookAt.height/2f, 2)
		text.append("\nFPS: ").append(Gdx.graphics.framesPerSecond).append('\n').append(DEBUG_DRAW_INFO)
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