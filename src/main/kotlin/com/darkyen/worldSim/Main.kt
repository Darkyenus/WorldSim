@file:JvmName("Main")
package com.darkyen.worldSim

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Game
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.resolvers.LocalFileHandleResolver
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.ObjectMap
import java.lang.NullPointerException

/**
 *
 */
object WorldSim : Game() {

	val assetManager = AssetManager(LocalFileHandleResolver()).apply {
		load("UISkin.json", Skin::class.java)
		load("World.atlas", TextureAtlas::class.java)
		finishLoading()
	}

	val batch = SpriteBatch()
	val skin:Skin = assetManager.get<Skin>("UISkin.json")
	val worldAtlas:TextureAtlas = assetManager.get<TextureAtlas>("World.atlas")
	val sprites = run {
		val allRegions = worldAtlas.regions
		val tiles = arrayOfNulls<TextureAtlas.AtlasRegion>(allRegions.size)
		for (region in allRegions) {
			val tilePrefix = "tile"
			if (region.name.startsWith(tilePrefix)) {
				tiles[region.name.removePrefix(tilePrefix).toInt()] = region
			}
		}
		val truncated = tiles.dropLastWhile { it == null }
		val first = truncated[0] ?: throw NullPointerException("Tile 0 must exist")
		truncated.map { it ?: first }.toTypedArray()
	}

	val debugFont = BitmapFont().apply {
		setUseIntegerPositions(false)
		setFixedWidthGlyphs("1234567890")
	}

	override fun create() {
		setScreen(WorldSimGame())
	}
}

val ARGS = ObjectMap<String, String?>()

fun main(args: Array<String>) {
	val c = Lwjgl3ApplicationConfiguration()
	c.setTitle("World Sim")
	c.useVsync(true)
	c.setResizable(true)
	c.setWindowedMode(800, 600)
	c.setWindowSizeLimits(200, 150, 40000, 30000)
	ARGS.ensureCapacity(args.size)
	for (arg in args) {
		val splitIndex = arg.indexOf(':')
		if (splitIndex == -1) {
			ARGS.put(arg, null)
		} else {
			ARGS.put(arg.substring(0, splitIndex), arg.substring(splitIndex + 1))
		}
	}
	Lwjgl3Application(object : ApplicationListener {
		override fun render() {
			WorldSim.render()
		}

		override fun pause() {
			WorldSim.pause()
		}

		override fun resume() {
			WorldSim.resume()
		}

		override fun resize(width: Int, height: Int) {
			WorldSim.resize(width, height)
		}

		override fun create() {
			WorldSim.create()
		}

		override fun dispose() {
			WorldSim.dispose()
		}
	}, c)
}
