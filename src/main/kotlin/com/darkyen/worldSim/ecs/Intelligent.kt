package com.darkyen.worldSim.ecs

import com.github.antag99.retinazer.Component
import com.github.antag99.retinazer.systems.FamilyWatcherSystem
import com.github.antag99.retinazer.util.Mask

/**
 *
 */
class IntelligentC : Component {

}

class IntelligentS : FamilyWatcherSystem(COMPONENT_DOMAIN.familyWith(IntelligentC::class.java)) {
	override fun insertedEntities(entities: Mask?) {
		TODO("not implemented")
	}

	override fun removedEntities(entities: Mask?) {
		TODO("not implemented")
	}

}