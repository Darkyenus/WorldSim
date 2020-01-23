package com.darkyen.worldSim.ai

import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resumeWithException

/** Function that is called forever and does stuff. */
typealias AIBrain = (suspend () -> Unit)

inline fun loop(action:()->Unit):Nothing {
	while (true) {
		action()
	}
}

private object GuardFailed : Throwable("Guard failed", null, false, false)

suspend fun guard(guard:() -> Boolean, activity:suspend ()->Unit):Boolean {
	try {
		withContext(object : ContinuationInterceptor {
			override val key = ContinuationInterceptor

			override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
				return object : Continuation<T> {
					override val context: CoroutineContext
						get() = continuation.context

					override fun resumeWith(result: Result<T>) {
						if (result.isFailure) {
							continuation.resumeWith(result)
						} else {
							val check = guard()
							println("Checking conditions, looking $check")
							if (check) {
								continuation.resumeWith(result)
							} else {
								continuation.resumeWithException(GuardFailed)
							}
						}
					}
				}
			}
		}) {
			activity()
		}
		return true
	} catch (e :GuardFailed) {
		return false
	}
}