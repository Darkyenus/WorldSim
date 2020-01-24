package com.darkyen.worldSim.util

typealias GdxByteArray = com.badlogic.gdx.utils.ByteArray
typealias GdxShortArray = com.badlogic.gdx.utils.ShortArray
typealias GdxIntArray = com.badlogic.gdx.utils.IntArray
typealias GdxLongArray = com.badlogic.gdx.utils.LongArray
typealias GdxFloatArray = com.badlogic.gdx.utils.FloatArray
typealias GdxArray<T> = com.badlogic.gdx.utils.Array<T>

/** Iterate through a [GdxIntArray] */
inline fun GdxIntArray.forEach(op:(Int)->Unit) {
	val size = this.size
	val items = this.items
	for (i in 0 until size) {
		op(items[i])
	}
}

/** Find first item which satisfies the predicate and return its index or -1 if no such element exists. */
inline fun GdxIntArray.findIndex(op:(Int)->Boolean):Int {
	val size = this.size
	val items = this.items
	for (i in 0 until size) {
		if (op(items[i])) {
			return i
		}
	}
	return -1
}

/** Find first item which satisfies the predicate and return it or -1 if no such element exists. */
inline fun GdxIntArray.find(op:(Int)->Boolean):Int {
	val size = this.size
	val items = this.items
	for (i in 0 until size) {
		val item = items[i]
		if (op(item)) {
			return item
		}
	}
	return -1
}

fun ByteArray.indexOfMax():Int {
	var maxIndex = 0
	var maxValue = this[0]
	for (i in 1 until size) {
		val value = this[i]
		if (value > maxValue) {
			maxValue = value
			maxIndex = i
		}
	}
	return maxIndex
}