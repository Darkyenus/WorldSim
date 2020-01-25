package com.darkyen.worldSim.util

import com.badlogic.gdx.math.RandomXS128
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 *
 */
class WeightedSamplerTest {

	@Test
	fun weightedSamplerSanityTestEqual() {
		val sampler = WeightedSampler(
				Weighted(0, 100f),
				Weighted(1, 100f)
		)

		val samples = IntArray(2)
		val random = RandomXS128()
		for (i in 0 until 1000) {
			samples[sampler.sample(random)]++
		}

		Assertions.assertTrue(samples[0] > 400)
		Assertions.assertTrue(samples[1] > 400)
		Assertions.assertEquals(1000, samples.sum())
	}

	@Test
	fun weightedSamplerSanityTestSkewed() {
		val sampler = WeightedSampler(
				Weighted(0, 1f),
				Weighted(1, 99f)
		)

		val samples = IntArray(2)
		val random = RandomXS128()
		for (i in 0 until 1000) {
			samples[sampler.sample(random)]++
		}

		Assertions.assertTrue(samples[0] < 50, samples.contentToString())
		Assertions.assertTrue(samples[1] >= 850, samples.contentToString())
		Assertions.assertEquals(1000, samples.sum(), samples.contentToString())
	}

	@Test
	fun weightedSamplerSanityTestMulti3() {
		val sampler = WeightedSampler(
				Weighted(0, 1f),
				Weighted(1, 2f),
				Weighted(2, 3f)
		)

		val samples = IntArray(3)
		val random = RandomXS128()
		for (i in 0 until 1000) {
			samples[sampler.sample(random)]++
		}

		Assertions.assertTrue(samples[0] < samples[1], samples.contentToString())
		Assertions.assertTrue(samples[1] < samples[2], samples.contentToString())
		Assertions.assertEquals(1000, samples.sum())
	}

	@Test
	fun weightedSamplerSanityTestMulti4() {
		val sampler = WeightedSampler(
				Weighted(0, 1f),
				Weighted(1, 2f),
				Weighted(2, 3f),
				Weighted(3, 4f)
		)

		val samples = IntArray(4)
		val random = RandomXS128()
		for (i in 0 until 1000) {
			samples[sampler.sample(random)]++
		}

		Assertions.assertTrue(samples[0] < samples[1], samples.contentToString())
		Assertions.assertTrue(samples[1] < samples[2], samples.contentToString())
		Assertions.assertTrue(samples[2] < samples[3], samples.contentToString())
		Assertions.assertEquals(1000, samples.sum())
	}

}