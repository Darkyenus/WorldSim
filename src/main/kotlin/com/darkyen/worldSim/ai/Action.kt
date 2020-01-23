package com.darkyen.worldSim.ai

import com.darkyen.worldSim.Item
import com.darkyen.worldSim.util.Vec2

/** A way to command the agent. */
interface Action {

	/** Move to some position */
	fun moveTo(pos: Vec2):Activity

	/** Pick up a given amount of items from the tile. */
	fun pickUp(item: Item, amount:Int):Activity
	/** Put down a given amount of items on the tile. */
	fun putDown(item: Item, amount:Int):Activity

	/** Some task that happens for some time. */
	interface Activity {
		val done:Boolean

		fun cancel()
	}
}