package com.darkyen.worldSim.util

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion
import com.badlogic.gdx.graphics.g2d.TextureRegion

/** How many pixels in each direction does one tile have */
const val TILE_PIXELS = 16

/** Bits of a white color. */
val WHITE_BITS = Color.WHITE.toFloatBits()

/**
 * Draw [AtlasRegion] horizontally centered.
 */
fun AtlasRegion.renderCentered(batch: Batch, centerX: Float, bottomY: Float, scale: Float, flipX: Boolean) {
	val invTilePixels = scale / TILE_PIXELS
	var w = this.packedWidth * invTilePixels
	val h = this.packedHeight * invTilePixels
	var x = centerX + this.offsetX * invTilePixels - this.originalWidth * invTilePixels * 0.5f
	val y = bottomY + this.offsetY * invTilePixels
	if (flipX) {
		x += w
		w = -w
	}
	batch.draw(this, x, y, w, h)
}

fun AtlasRegion.render(batch: Batch, x: Float, y: Float, w: Float, h: Float) {
	val originalWidthInv = 1f / originalWidth
	val originalHeightInv = 1f / originalHeight
	val xScl = packedWidth * originalWidthInv
	val yScl = packedHeight * originalHeightInv
	batch.draw(this,
			x + offsetX * w * originalWidthInv,
			y + offsetY * h * originalHeightInv,
			w * xScl, h * yScl)
}

fun AtlasRegion.render(batch: Batch, x: Float, y: Float, w: Float, h: Float, rotationDeg:Float) {
	val originalWidthInv = 1f / originalWidth
	val originalHeightInv = 1f / originalHeight
	val xScl = packedWidth * originalWidthInv
	val yScl = packedHeight * originalHeightInv
	val width = w * xScl
	val height = h * yScl

	batch.draw(this,
			x + offsetX * w * originalWidthInv,
			y + offsetY * h * originalHeightInv,
			width * 0.5f, height * 0.5f,
			width, height,
			1f, 1f, rotationDeg)
}

private val RENDER_TILE_VERTICES = FloatArray(4*5).apply {
	this[2] = WHITE_BITS
	this[7] = WHITE_BITS
	this[12] = WHITE_BITS
	this[17] = WHITE_BITS
}
/** Renders region as a tile. Tiles are assumed to have no packing cut off and must be precise to not leave any gaps. */
fun TextureRegion.renderTile(batch:Batch, x:Int, y:Int) {
	val vertices = RENDER_TILE_VERTICES
	val x1 = x.toFloat()
	val y1 = y.toFloat()
	val x2 = (x + 1).toFloat()
	val y2 = (y + 1).toFloat()
	val u1 = this.u
	val v1 = this.v2
	val u2 = this.u2
	val v2 = this.v
	vertices[0] = x1
	vertices[1] = y1
	vertices[3] = u1
	vertices[4] = v1

	vertices[5] = x1
	vertices[6] = y2
	vertices[8] = u1
	vertices[9] = v2

	vertices[10] = x2
	vertices[11] = y2
	vertices[13] = u2
	vertices[14] = v2

	vertices[15] = x2
	vertices[16] = y1
	vertices[18] = u2
	vertices[19] = v1

	batch.draw(texture, vertices, 0, 20)
}