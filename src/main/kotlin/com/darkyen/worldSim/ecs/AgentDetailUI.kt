package com.darkyen.worldSim.ecs

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.darkyen.worldSim.ITEMS
import com.darkyen.worldSim.RenderService
import com.darkyen.worldSim.WorldSim
import com.darkyen.worldSim.WorldSimGame
import com.darkyen.worldSim.util.Vec2
import com.github.antag99.retinazer.Mapper
import com.github.antag99.retinazer.Wire
import kotlin.math.roundToInt

/**
 *
 */
class AgentDetailUI : RenderService, WorldSimGame.UILayerProvider {

	@Wire
	private lateinit var camera:CameraService

	@Wire
	private lateinit var positionC: Mapper<PositionC>
	@Wire
	private lateinit var agentC: Mapper<AgentC>
	@Wire
	private lateinit var agentSpatialLookup:AgentSpatialLookup

	private val selectDistance = 1

	override fun renderUpdate(delta: Float) {
		val pointingAt = camera.unproject(Gdx.input.x, Gdx.input.y)

		var nearestEntity = -1
		var nearestEntityDst2 = Float.POSITIVE_INFINITY

		agentSpatialLookup.forEntitiesNear(Vec2(pointingAt.x.roundToInt(), pointingAt.y.roundToInt()), selectDistance) { entity: Int, _: Int ->
			val position = positionC[entity] ?: return@forEntitiesNear
			val dist2 = pointingAt.dst2(position.pos.x.toFloat(), position.pos.y.toFloat())

			if (dist2 > selectDistance) {
				return@forEntitiesNear
			}

			if (dist2 < nearestEntityDst2) {
				nearestEntityDst2 = dist2
				nearestEntity = entity
			}
		}

		if (nearestEntity == -1) {
			uiLayer.isVisible = false
			return
		}

		uiLayer.isVisible = true

		entityId.setText(nearestEntity)
		val agent = agentC[nearestEntity]!!

		activity.setText(agent.activity.name)
		age.setText(agent.ageYears)

		for (attribute in AGENT_ATTRIBUTES) {
			attributes[attribute.ordinal].setText(agent.attributes[attribute].toInt())
		}

		for (item in ITEMS) {
			items[item.ordinal].setText(agent.inventory[item.ordinal])
		}
	}

	private val entityId = Label("?", WorldSim.skin)
	private val activity = Label("?", WorldSim.skin)
	private val age = Label("?", WorldSim.skin)
	private val attributes = AGENT_ATTRIBUTES.map {
		Label("?", WorldSim.skin)
	}
	private val items = ITEMS.map {
		Label("?", WorldSim.skin)
	}

	private val detailTable = Table(WorldSim.skin).apply {
		setBackground("default-round")
		pad(5f)
		columnDefaults(0).align(Align.right).padRight(15f)
		columnDefaults(1).align(Align.left)

		add("Entity ID")
		add(entityId)
		row().padTop(10f)
		add("Activity")
		add(activity).row()
		add("Age")
		add(age).row()

		for (attribute in AGENT_ATTRIBUTES) {
			add(attribute.name)
			add(attributes[attribute.ordinal]).row()
		}
		for (item in ITEMS) {
			add(item.name)
			add(items[item.ordinal]).row()
		}
	}

	override val uiLayer: Container<Table> = Container(detailTable)
			.align(Align.bottomRight)
			.pad(10f)
			.minSize(200f, 200f).apply {
				setFillParent(true)
			}
}