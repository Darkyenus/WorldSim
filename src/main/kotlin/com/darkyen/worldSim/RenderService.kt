package com.darkyen.worldSim

import com.github.antag99.retinazer.EngineService

/**
 *
 */
interface RenderService : EngineService {

	fun renderUpdate(delta:Float)

}