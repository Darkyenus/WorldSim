package com.darkyen.worldSim.ai

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Delay
import kotlinx.coroutines.InternalCoroutinesApi
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

class AICoroutineContext(val entity:Int) : AbstractCoroutineContextElement(AICoroutineContext) {
	companion object Key : CoroutineContext.Key<AICoroutineContext>
}

/**
 *
 */
@UseExperimental(InternalCoroutinesApi::class)
class AICoroutineDispatcher : ContinuationInterceptor, Delay, CoroutineScope {

	override val key: CoroutineContext.Key<*>
		get() = ContinuationInterceptor

	override val coroutineContext: CoroutineContext
		get() = this

	override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
		TODO("not implemented")
	}

	override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
		TODO("not implemented")
	}
}