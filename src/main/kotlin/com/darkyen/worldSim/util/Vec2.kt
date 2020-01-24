package com.darkyen.worldSim.util

import kotlin.math.abs

private const val COORD_MASK:Long = 0x7FFF_FFFFL
private const val PACKED_MASK:Long = 0x7FFF_FFFF_7FFF_FFFFL
/** Garbage-less 2D integer vector. Supports 31-bit precision on each axis. */
inline class Vec2(val packed:Long) {

	constructor(x:Int, y:Int):this(((x.toLong() and COORD_MASK) shl 32) or (y.toLong() and COORD_MASK))

	val x:Int
		get() = ((packed shl 1) shr 33).toInt()

	val y:Int
		get() = ((packed shl 33) shr 33).toInt()

	operator fun plus(other:Vec2):Vec2 {
		return Vec2((packed + other.packed) and PACKED_MASK)
	}

	operator fun unaryMinus():Vec2 {
		return Vec2(((packed xor PACKED_MASK) + 0x0000_0001_0000_0001L) and PACKED_MASK)
	}

	operator fun minus(other:Vec2):Vec2 {
		return Vec2((packed + (other.packed xor PACKED_MASK) + 0x0000_0001_0000_0001L) and PACKED_MASK)
	}

	operator fun times(factor:Int):Vec2 {
		return Vec2(x * factor, y * factor)
	}

	operator fun div(factor:Int):Vec2 {
		return Vec2(x / factor, y / factor)
	}

	/** Euclidean length, squared */
	val len2:Int
		get() {
			val x = this.x
			val y = this.y
			return x*x + y*y
		}

	/** Euclidean length */
	val len:Float
		get() = kotlin.math.sqrt(len2.toFloat())

	/** Manhattan length */
	val manhLen:Int
		get() = abs(x) + abs(y)

	/** Manhattan distance */
	fun manhDst(to:Vec2):Int = (this - to).manhLen

	override fun toString(): String = "[$x, $y]"

	companion object {
		/** Unique zero vector, different from standard zero vector, but behaving the same. */
		val NULL = Vec2(PACKED_MASK.inv())
		val UP = Vec2(0, 1)
		val DOWN = Vec2(0, -1)
		val LEFT = Vec2(-1, 0)
		val RIGHT = Vec2(1, 0)

		val ZERO = Vec2(0L)
	}
}