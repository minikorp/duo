package com.minikorp.duo

/**
 * Base type for action handling.
 *
 * Action handling will happen after all middlewares had a chance to observe passing actions.
 *
 * Reducers can be combined using the + operator. Call order will be sequential.
 */
fun interface Reducer<T : Any> {
    suspend fun reduce(ctx: ActionContext<T>)
}

/**
 * Identity reducer that does nothing.
 */
@Suppress("FunctionName", "UNCHECKED_CAST")
fun <T : Any> IdentityReducer(): Reducer<T> = IdentityReducer as Reducer<T>

object IdentityReducer : Reducer<Any> {
    override suspend fun reduce(ctx: ActionContext<Any>) = Unit
}

class CombinedReducer<T : Any>(
    private val reducers: List<Reducer<T>>
) : Reducer<T> {
    override suspend fun reduce(ctx: ActionContext<T>) {
        reducers.forEach { combinedHandler ->
            combinedHandler.reduce(ctx)
        }
    }
}

/**
 * Compose a list of handlers a single une to plug into a [Store]
 */
fun <T : Any> combineHandlers(vararg handlers: Reducer<T>): Reducer<T> {
    return CombinedReducer(handlers.toList())
}

operator fun <T : Any> Reducer<T>.plus(other: Reducer<T>): Reducer<T> {
    return CombinedReducer(listOf(this, other))
}
