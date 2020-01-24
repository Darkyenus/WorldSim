package com.darkyen.worldSim.ecs

import com.badlogic.gdx.math.MathUtils
import com.darkyen.worldSim.EntityChunkPopulator
import com.darkyen.worldSim.ITEMS
import com.darkyen.worldSim.ai.AIBrain
import com.darkyen.worldSim.ai.AIContext
import com.darkyen.worldSim.ai.AICoroutineManager
import com.darkyen.worldSim.ecs.AgentAttribute.ALERTNESS
import com.darkyen.worldSim.util.forEach
import com.github.antag99.retinazer.Component
import com.github.antag99.retinazer.Mapper
import com.github.antag99.retinazer.Wire
import com.github.antag99.retinazer.systems.FamilyWatcherSystem
import com.github.antag99.retinazer.util.Bag
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.random.Random

/**
 *
 */
class AgentC(val brain:AIBrain, val genderMale:Boolean) : Component {
	val inventory = IntArray(ITEMS.size)
	var ageYears:Int = 0

	val attributes = ByteArray(AGENT_ATTRIBUTES.size)

	var activity:AgentActivity = AgentActivity.IDLE

	val isBaby:Boolean
		get() = ageYears < MATURITY_AGE_YEAR

	fun attributePercent(attribute:AgentAttribute):Float {
		return (attributes[attribute] - attribute.min) / (attribute.max - attribute.min).toFloat()
	}

	/** Check if a cognitive function should fail, due to lack of sleep, water, panic, etc. */
	fun misfire():Boolean {
		val health = 10 - attributes[AgentAttribute.HEALTH]
		val food = -50 - attributes[AgentAttribute.HUNGER]
		val water = -10 - attributes[AgentAttribute.THIRST]
		val sleep = 20 - attributes[AgentAttribute.SLEEP]
		val panic = 30 - (attributes[ALERTNESS] - attributes[AgentAttribute.MENTAL_STRENGTH])

		val misfireChance = max(max(max(health, food), max(water, sleep)), panic)
		if (misfireChance <= 0) {
			return false
		}
		return misfireChance > Random.nextInt(100)
	}
}

enum class AgentActivity(val sprite:Int, val canListen:Boolean = false) {
	IDLE(-1, canListen = true),
	WALKING(-1),
	DRINKING(86, canListen = true),
	EATING(87, canListen = true),
	SLEEPING(88),
	GATHERING_MUSHROOMS(89, canListen = true),
	GATHERING_FRUIT(90, canListen = true),
	HUNTING(91),
	PANICKING(92),
	TALKING(93, canListen = true),
	REFILLING_CANTEEN(100, canListen = true),
	GATHERING_CRAFTING_MATERIAL(96, canListen = true),
	GATHERING_WOOD(97, canListen = true),
	GATHERING_STONE(98, canListen = true),
	CRAFTING(99, canListen = true),
}

enum class AgentAttribute(
		/** Min value of this attribute. */
		val min:Byte = 0,
		/** Max value for this attribute. */
		val max:Byte = 100) {
	// Dynamic (needs)
	/** Injuries, etc. */
	HEALTH,
	/** Hunger */
	HUNGER(min = -100),
	/** Thirst */
	THIRST(min = -100),
	/** Lack of sleep */
	SLEEP(min=-100),
	/** Dangerous situations increase alertness, goes down over time. */
	ALERTNESS,
	/** Being alone increases the need to talk with others. Affected by extroversion. */
	SOCIAL,
	// Other needs: clothing, shelter, family, society

	// Static
	/** How physically strong the person is */
	STRENGTH,
	/** How mentally strong the person is. Low mental strength leads to lower panic threshold. */
	MENTAL_STRENGTH(min=-100),
	/** Affects walking speed etc. */
	AGILITY,
	/** People with high endurance suffer less from low health and are less likely to die when deprived of basic needs. */
	ENDURANCE,
	/** How much extroverted (or introverted) the person is. */
	EXTROVERSION
}

val AGENT_ATTRIBUTES = AgentAttribute.values()

operator fun ByteArray.get(attr:AgentAttribute):Byte {
	return this[attr.ordinal]
}

operator fun ByteArray.set(attr:AgentAttribute, amount:Byte) {
	this[attr.ordinal] = MathUtils.clamp(amount.toInt(), attr.min.toInt(),  attr.max.toInt()).toByte()
}

operator fun ByteArray.set(attr:AgentAttribute, amount:Int) {
	this[attr.ordinal] = MathUtils.clamp(amount, attr.min.toInt(),  attr.max.toInt()).toByte()
}

/** When entities reach this year, their sprite changes from child sprite to mature sprite. */
const val MATURITY_AGE_YEAR = 16

const val DAY_LENGTH_IN_REAL_SECONDS = 60f * 5f // 5 min = 1 day
const val HOUR_LENGTH_IN_REAL_SECONDS = DAY_LENGTH_IN_REAL_SECONDS / 24f
const val HOUR_LENGTH_IN_MS = (HOUR_LENGTH_IN_REAL_SECONDS * 1000).toInt()

class AgentS : FamilyWatcherSystem.Single(COMPONENT_DOMAIN.familyWith(PositionC::class.java, AgentC::class.java)) {

	@Wire
	lateinit var world: World
	@Wire
	lateinit var positionC: Mapper<PositionC>
	@Wire
	lateinit var agentC: Mapper<AgentC>
	@Wire
	lateinit var renderC: Mapper<RenderC>
	@Wire
	lateinit var pathFinder:PathFinder

	private val brainSurgeon = AICoroutineManager()

	val currentTimeMs:Long
		get() = brainSurgeon.currentNanoTime / 1000_000L

	private var dayProgressSec = 0f
	// Days elapsed
	private var yearProgress = 0
	private val yearLengthDays = 50

	private val waitingContinuation = Bag<Continuation<Unit>>()

	fun continueEntity(entity:Int) {
		waitingContinuation.get(entity)!!.resume(Unit)
	}

	suspend fun waitEntity(entity:Int, activity:AgentActivity) {
		val agent: AgentC = agentC[entity]
		val oldActivity = agent.activity
		agent.activity = activity
		try {
			suspendCoroutine<Unit> {
				waitingContinuation.set(entity, it)
			}
		} finally {
			agent.activity = oldActivity
		}
	}


	override fun update(delta: Float) {
		super.update(delta)
		dayProgressSec += delta
		yearProgress += if (dayProgressSec >= DAY_LENGTH_IN_REAL_SECONDS) {
			dayProgressSec -= DAY_LENGTH_IN_REAL_SECONDS
			1
		} else 0
		if (yearProgress >= yearLengthDays) {
			yearProgress -= yearLengthDays
			advanceYear()
		}

		brainSurgeon.update(delta)
	}

	private fun advanceYear() {
		// Age everyone by 1 year
		entities.indices.forEach { entity ->
			val agent = agentC[entity]
			agent.ageYears += 1
			if (agent.ageYears == MATURITY_AGE_YEAR) {
				// Update sprite from child sprite
				renderC[entity]?.let {
					it.sprite = (if (agent.genderMale) EntityChunkPopulator.maleSprites else EntityChunkPopulator.femaleSprites).random()
				}
			}
		}
	}

	override fun insertedEntity(entity: Int, delta: Float) {
		brainSurgeon.beginBrain(AIContext(entity, this, agentC[entity]!!, positionC[entity]!!))
	}

	override fun removedEntity(entity: Int, delta: Float) {
		brainSurgeon.endBrain(entity)
	}
}

