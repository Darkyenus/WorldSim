package com.darkyen.worldSim.ecs

import com.darkyen.worldSim.util.Vec2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 *
 */
class WorldTest {

	val PRECISION_MASK = 0x7FFF_FFFF

	@Test
	fun posTest() {
		val criticalValues = arrayOf(0, 1, 2, 3, -1, -2, -3, 10000, -10000, 0x1FFF_FFFF, -0x1FFF_FFFF, 0x3FFF_FFFF, -0x3FFF_FFFF)
		for (x in criticalValues) {
			for (y in criticalValues) {
				val xy = Vec2(x, y)

				assertEquals(x, xy.x)
				assertEquals(y, xy.y)
			}
		}

		val allCombinations = criticalValues.flatMap { criticalValues.zip(Array(criticalValues.size){ it }) }
		for ((x1, x2) in allCombinations) {
			for ((y1, y2) in allCombinations) {
				val xy1 = Vec2(x1, y1)
				val xy2 = Vec2(x2, y2)
				val sum = xy1 + xy2
				assertEquals((x1 + x2) and PRECISION_MASK shl 1 shr 1, sum.x, "$x1 + $x2")
				assertEquals((y1 + y2) and PRECISION_MASK shl 1 shr 1, sum.y, "$y1 + $y2")

				val diff = xy1 - xy2
				assertEquals((x1 - x2) and PRECISION_MASK shl 1 shr 1, diff.x, "$x1 + $x2")
				assertEquals((y1 - y2) and PRECISION_MASK shl 1 shr 1, diff.y, "$y1 + $y2")
			}
		}
	}

}