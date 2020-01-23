package com.darkyen.worldSim.util

typealias GdxByteArray = com.badlogic.gdx.utils.ByteArray
typealias GdxShortArray = com.badlogic.gdx.utils.ShortArray
typealias GdxIntArray = com.badlogic.gdx.utils.IntArray
typealias GdxLongArray = com.badlogic.gdx.utils.LongArray
typealias GdxFloatArray = com.badlogic.gdx.utils.FloatArray
typealias GdxArray<T> = com.badlogic.gdx.utils.Array<T>

/**
 * Iterate through a [GdxIntArray]
 */
inline fun GdxIntArray.forEach(op:(Int)->Unit) {
	val size = this.size
	val items = this.items
	for (i in 0 until size) {
		op(items[i])
	}
}

fun <T> pick(seed:Int, vararg variants:T):T {
	return variants[(seed and 0x7FFF_FFFF) % variants.size]
}