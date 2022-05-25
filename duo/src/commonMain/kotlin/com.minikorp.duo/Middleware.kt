package com.minikorp.duo

/**
 * Middleware that will be called for every dispatch to modify the
 * action or perform side effects like logging.
 *
 * Call chain.proceed(action) with the new action to continue the chain.
 *
 * Middlewares wrap the process of dispatching in nesting structure
 *
 * ┌──> middleware 1
 * │  ┌──> middleware 2
 * │  │     >>> Reducer
 * │  └──<
 * └──< middleware 1
 *
 * @property priority Determine the order this middleware will be called in the chain.
 * Higher value means earlier call. Defaults to 100.
 *
 */
interface Middleware<S : Any> {
    val priority: Int get() = 100
    suspend fun intercept(ctx: ActionContext<S>, chain: Chain<S>)
}

/**
 * A chain of interceptors. Call [proceed] with
 * the intercepted action or directly handle it.
 */
fun interface Chain<S : Any> {
    suspend fun proceed(ctx: ActionContext<S>)
}

inline fun <reified T : Middleware<*>> Store<*>.findMiddleware(): T? {
    return middlewares.find { it is T }?.let { it as? T }
}

inline fun <reified T : Middleware<*>> Store<*>.requireMiddleware(): T {
    return findMiddleware()
        ?: throw IllegalStateException(
            "Middleware of type ${T::class} required but not found, " +
                    "existing middlewares: $middlewares",
        )
}
