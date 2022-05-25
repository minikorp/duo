package com.minikorp.duo

import kotlinx.coroutines.flow.*
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Run the side effect every time the value changes from [before] to [after].
 */
inline fun <T> Flow<T>.onEachChange(
    before: T,
    after: T,
    crossinline fn: suspend (value: T) -> Unit
): Flow<T> {
    return runningReduce { prev, next ->
        if (before == prev && after == next) fn(next)
        next
    }
}

/**
 * Run the side effect every time the value changes based on [didChange].
 */
inline fun <T> Flow<T>.onEachChange(
    crossinline didChange: (prev: T, next: T) -> Boolean,
    crossinline fn: suspend (value: T) -> Unit
): Flow<T> {
    return runningReduce { prev, next ->
        if (didChange(prev, next)) fn(next)
        next
    }
}

/**
 * Same as [Flow.map] but for [StateFlow].
 */
inline fun <T : Any, R> StateFlow<T>.select(
    crossinline selector: (T) -> R,
): StateFlow<R> {
    val source = this

    return object : StateFlow<R> {
        override val replayCache: List<R> get() = source.replayCache.map(selector)
        override val value: R get() = source.value.let(selector)
        override suspend fun collect(collector: FlowCollector<R>): Nothing {
            source.collect { value -> collector.emit(selector(value)) }
        }
    }
}