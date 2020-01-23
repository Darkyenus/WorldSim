package com.darkyen.worldSim.util

import java.util.*

/**
 * Sampling of weighted discrete values.
 * Implements Vose's Alias algorithm described by Keith Schwarz at [https://www.keithschwarz.com/darts-dice-coins/].
 */
class WeightedSampler<T>(vararg items:Weighted<T>) {

	private val items:Array<Any?> = Array(items.size) { items[it].value }
	private val probability = FloatArray(items.size) { 1f }
	private val alias = IntArray(items.size)

	init {
		assert(items.isNotEmpty())

		val normalizationFactor = items.size / items.fold(0f) { acc, w -> acc + w.weight }
		val workingProbabilities = FloatArray(items.size) { items[it].weight * normalizationFactor }

		val small = IntArray(items.size)
		val large = IntArray(items.size)
		var smallI = 0
		var largeI = 0

		for (i in items.indices) {
			if (workingProbabilities[i] < 1f) {
				small[smallI++] = i
			} else {
				large[largeI++] = i
			}
		}

		val probability = probability
		val alias = alias

		while (smallI > 0 && largeI > 0) {
			val l = small[--smallI]
			val g = large[--largeI]

			probability[l] = workingProbabilities[l]
			alias[l] = g

			val pg = workingProbabilities[g] + workingProbabilities[l] - 1f
			workingProbabilities[g] = pg

			if (pg < 1f) {
				small[smallI++] = g
			} else {
				large[largeI++] = g
			}
		}
	}

	fun sample(random: Random):T {
		val probability = probability
		val column = random.nextInt(probability.size)
		val index = if (random.nextFloat() < probability[column]) column else alias[column]

		@Suppress("UNCHECKED_CAST")
		return items[index] as T
	}
}

class Weighted<T>(val value:T, val weight:Float)
