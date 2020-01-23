package com.darkyen.worldSim.ai

import com.badlogic.gdx.Gdx
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.Delayed
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 *
 */
class AICoroutineExecutorService(val maxRealTimePerUpdateNanos:Long = 10_000_000L) : ScheduledExecutorService {

	private var inShutdown = false
	private var currentNanoTime = 0L

	private val queue = PriorityQueue<AIFuture<*>>(128)

	fun update(nano:Long) {
		currentNanoTime += nano
		processQueue()
	}

	fun updateParallel(nano:Long, realExecutor: ExecutorService, parallelism:Int) {
		currentNanoTime += nano
		val futures = Array(max(0, parallelism - 1)) {
			realExecutor.submit(this@AICoroutineExecutorService::processQueue)
		}
		// So this thread also does something
		processQueue()
		for (future in futures) {
			try {
				future.get()
			} catch (e:Exception) {
				Gdx.app.log("AICoroutineExecutorService", "Parallel processQueue() failed", e)
			}
		}
	}

	private fun processQueue() {
		val currentNanoTime = currentNanoTime
		val queue = queue
		val inShutdown = inShutdown

		val realTimeOfForcedEnd = System.nanoTime() + maxRealTimePerUpdateNanos

		var update = 0
		var toReschedule:AIFuture<*>? = null
		while (true) {
			val task = synchronized(queue) {
				if (toReschedule != null) {
					queue.add(toReschedule)
				}

				val first = queue.peek() ?: return
				if (first.executeAtNanoTime > currentNanoTime) {
					return
				}
				assert(queue.remove() === first)
				first
			}

			toReschedule = task.compute()
			if (toReschedule != null && inShutdown) {
				toReschedule.cancel(false)
				toReschedule = null
			}

			update++

			if ((update and 0b111) == 0) {
				if (System.nanoTime() >= realTimeOfForcedEnd) {
					// Forced end!
					break
				}
			}
		}
	}

	//region Task intake
	private fun <T> submit(future:AIFuture<T>):AIFuture<T> {
		if (inShutdown) {
			throw RejectedExecutionException("Shutting down")
		}
		synchronized(queue) {
			queue.add(future)
		}
		return future
	}

	override fun submit(task: Runnable): Future<*> {
		return submit(AIFuture<Any?>(task, null, currentNanoTime, 0))
	}

	override fun <T : Any?> submit(task: Callable<T>): Future<T> {
		return submit(AIFuture(null, task, currentNanoTime, 0))
	}

	override fun <T : Any?> submit(task: Runnable, result: T): Future<T> {
		return submit(AIFuture<T>(task, null, currentNanoTime, 0).also { it.result = result })
	}

	override fun execute(command: Runnable) {
		submit(AIFuture<Any?>(command, null, currentNanoTime, 0))
	}

	override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
		return submit(AIFuture<Any?>(command, null, currentNanoTime + unit.toNanos(delay), 0))
	}

	override fun <V : Any?> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> {
		return submit(AIFuture(null, callable, currentNanoTime + unit.toNanos(delay), 0))
	}

	override fun scheduleAtFixedRate(command: Runnable, initialDelay: Long, period: Long, unit: TimeUnit): ScheduledFuture<*> {
		assert(period > 0)
		return submit(AIFuture<Any?>(command, null, currentNanoTime + unit.toNanos(initialDelay), unit.toNanos(period)))
	}

	override fun scheduleWithFixedDelay(command: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
		assert(delay > 0)
		return submit(AIFuture<Any?>(command, null, currentNanoTime + unit.toNanos(initialDelay), -unit.toNanos(delay)))
	}

	//endregion

	//region Invoke

	override fun <T : Any?> invokeAny(tasks: MutableCollection<out Callable<T>>): T {
		throw RejectedExecutionException("Not supported")
	}

	override fun <T : Any?> invokeAny(tasks: MutableCollection<out Callable<T>>, timeout: Long, unit: TimeUnit): T {
		throw RejectedExecutionException("Not supported")
	}

	override fun <T : Any?> invokeAll(tasks: MutableCollection<out Callable<T>>): MutableList<Future<T>> {
		throw RejectedExecutionException("Not supported")
	}

	override fun <T : Any?> invokeAll(tasks: MutableCollection<out Callable<T>>, timeout: Long, unit: TimeUnit): MutableList<Future<T>> {
		throw RejectedExecutionException("Not supported")
	}

	//endregion

	//region Termination

	override fun isTerminated(): Boolean {
		return inShutdown && synchronized(queue) { queue.isEmpty() }
	}

	override fun shutdown() {
		inShutdown = true
	}

	override fun shutdownNow(): MutableList<Runnable> {
		inShutdown = true
		return synchronized(queue) {
			val result:MutableList<Runnable> = queue.toMutableList()
			queue.clear()
			result
		}
	}

	override fun isShutdown(): Boolean = inShutdown

	override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
		// No waiting!
		return inShutdown
	}

	//endregion

	private inner class AIFuture<T>(
			var runnable:Runnable?,
			var callable:Callable<T>?,
			var executeAtNanoTime:Long,
			val rescheduleAfter:Long
	) : ScheduledFuture<T>, Runnable {

		val remainingNanos:Long
			get() = executeAtNanoTime - currentNanoTime

		var result:Any? = null
		val state = AtomicInteger(STATE_NEW)

		fun compute():AIFuture<T>? {
			val runnable = runnable
			val callable = callable

			if (state.get() == STATE_CANCELLED) {
				this.runnable = null
				this.callable = null
				return null
			}

			var resultState = STATE_DONE
			when {
				runnable != null -> {
					try {
						runnable.run()
					} catch (e:Throwable) {
						resultState = STATE_FAILED
						result = e
					}
				}
				callable != null -> {
					try {
						result = callable.call()
					} catch (e:Throwable) {
						resultState = STATE_FAILED
						result = e
					}
				}
				else -> throw IllegalStateException("Already computed")
			}

			if (resultState == STATE_DONE && rescheduleAfter != 0L && state.get() == STATE_NEW) {
				executeAtNanoTime = if (rescheduleAfter < 0L) {
					currentNanoTime - rescheduleAfter
				} else {
					executeAtNanoTime + rescheduleAfter
				}
				return this
			}

			state.compareAndSet(STATE_NEW, resultState)
			this.runnable = null
			this.callable = null
			return null
		}

		override fun run() {
			compute()
		}

		override fun compareTo(other: Delayed?): Int {
			if (other == null) {
				return 1
			}
			return remainingNanos.compareTo(other.getDelay(TimeUnit.NANOSECONDS))
		}

		override fun isDone(): Boolean = state.get() != STATE_NEW

		override fun get(): T {
			while (true) {
				@Suppress("UNCHECKED_CAST")
				when (state.get()) {
					STATE_DONE -> return result as T
					STATE_CANCELLED -> throw CancellationException("AIFuture has been cancelled")
					STATE_FAILED -> throw ExecutionException(result as Throwable?)
					else -> throw InterruptedException("Can't wait on AIFuture")
				}
			}
		}

		override fun get(timeout: Long, unit: TimeUnit): T = get()

		override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
			return state.compareAndSet(STATE_NEW, STATE_CANCELLED)
		}

		override fun getDelay(unit: TimeUnit): Long = unit.convert(remainingNanos, TimeUnit.NANOSECONDS)

		override fun isCancelled(): Boolean = state.get() == STATE_CANCELLED
	}

	companion object {
		const val STATE_NEW = 0
		const val STATE_DONE = 1
		const val STATE_CANCELLED = 2
		const val STATE_FAILED = 3
	}
}