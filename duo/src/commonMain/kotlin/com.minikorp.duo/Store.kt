package com.minikorp.duo

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import kotlin.collections.HashMap

/**
 * A generic state holder that allow installation of [Middleware] and exposes a [states] flow to
 * observe state changes.
 *
 * @param initialState The initial value of the state
 * @param storeScope The default scope used for launching dispatch actions
 * @param reducer The action handler responsible for handling the actions and
 * performing state mutations.
 */
class Store<S : Any>(
    initialState: S,
    val storeScope: CoroutineScope,
    val reducer: Reducer<S>,
) {

    private val statesFlow = MutableStateFlow(initialState)

    /**
     * This flow will emit all **distinct** states changes produced by actions.
     *
     * Make sure the state class equality is properly implemented to avoid
     * hard to catch bugs.
     */
    val states: StateFlow<S> = statesFlow
    val state: S get() = statesFlow.value
    val middlewares: MutableList<Middleware<S>> = ArrayList()

    private val callerChain = Chain<S> { ctx ->
        reducer.reduce(ctx) // Final step of the chain just calls the reducer
        if (ctx.action is InlinedStateAction<*>) {
            @Suppress("UNCHECKED_CAST")
            setState(ctx.action.state as S)
        }
    }

    private var middlewareChain: Chain<S> = buildChain()

    /**
     * Set new state and notify listeners.
     *
     * As a general rule, this method should not be used directly since it will
     * skip middlewares and reducers, instead, dispatch actions to modify the state.
     *
     * Should only be called from the main thread.
     */
    fun setState(newState: S) {
        performStateChange(newState)
    }


    private fun performStateChange(newState: S) {
        //State mutation should happen on UI thread
        if (state != newState) {
            statesFlow.value = newState
        }
    }

    /**
     * Add a middleware to the chain, it will be called respecting [Middleware.priority].
     *
     * *This method is not thread safe*
     */
    fun addMiddleware(middleware: Middleware<S>) {
        middlewares.add(0, middleware)
        middlewares.sortBy { it.priority }
        middlewareChain = buildChain()
    }

    /**
     * Remove a middleware instance.
     */
    fun removeMiddleware(middleware: Middleware<S>) {
        middlewares.remove(middleware)
        middlewareChain = buildChain()
    }

    private fun buildChain(): Chain<S> {
        return middlewares.fold(callerChain) { chain, middleware ->
            Chain { context -> middleware.intercept(context, chain) }
        }
    }

    /**
     * Send an action through the middleware chain into the [Reducer]. This method
     * will suspend until all middlewares and action handler complete their job.
     *
     * This method can be called from any thread but will use
     * [Dispatchers.Main] when propagating the action.
     */
    suspend fun dispatch(action: Action, parentContext: ActionContext<*>? = null) {
        @Suppress("UNCHECKED_CAST")
        val actionContext = ActionContext<Any>(
            this@Store as Store<Any>,
            action,
            extra = parentContext?.extra ?: HashMap(),
        )

        withContext(Dispatchers.Main.immediate) {
            middlewareChain.proceed(actionContext)
        }
    }

    /**
     * Launch a new coroutine that will handle the action.
     *
     * The returned job will complete when all middlewares
     * and action handler complete their work.
     *
     * This method can be called from any thread.
     */
    fun offer(
        action: Action,
        parentContext: ActionContext<*>? = null,
    ): Job {
        return storeScope.launch(start = CoroutineStart.UNDISPATCHED) {
            dispatch(
                action,
                parentContext,
            )
        }
    }
}
