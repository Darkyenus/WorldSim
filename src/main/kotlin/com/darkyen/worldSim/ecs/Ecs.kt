package com.darkyen.worldSim.ecs

import com.github.antag99.retinazer.ComponentSet

/** Set of all used components. */
val COMPONENT_DOMAIN = ComponentSet(
		PositionC::class.java,
		CameraTrackerC::class.java,
		RenderC::class.java,
		AgentC::class.java,
		AgentSpeechC::class.java,
		DecayC::class.java,
		IntelligentC::class.java
		)