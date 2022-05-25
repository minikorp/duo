package com.minikorp.duo

import com.minikorp.drill.DrillType
import kotlinx.coroutines.Job


/**
 * Context that goes along every dispatched action.
 *
 * @property action Action being dispatched.
 * @property extra Extra information associated with this context.
 */
data class ActionContext<S : Any>(
    val store: Store<Any>,
    val action: Action,
    val extra: MutableMap<String, Any?>,
    @Suppress("UNCHECKED_CAST")
    private val getState: () -> S = { store.state as S },
    private val setState: (S) -> Unit = { store.setState(it) },
) {

    /**
     * Get the current state of the associated [Store].
     */
    val state: S get() = getState()

    var mutableState: S
        get() = getState()
        set(value) = setState(value)

    /**
     * Dispatch a new action to the [Store] while keeping the current context information.
     *
     * @param action The action to dispatch
     * @param parentContext The parent context, or null to avoid context propagation
     * @see [Store.dispatch]
     */
    suspend fun dispatch(action: Action, parentContext: ActionContext<S>?) {
        store.dispatch(action, parentContext)
    }

    /**
     * Offer a new action to the [Store] while keeping the current context information.
     *
     * @param action The action to dispatch
     * @param parentContext The parent context, or null to avoid context propagation
     * @see [Store.offer]
     */
    fun offer(action: Action, parentContext: ActionContext<S>?): Job {
        return store.offer(action, parentContext)
    }

    override fun toString(): String {
        return "DispatchContext(action=$action, extra=$extra)"
    }


    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: String): T? {
        return extra[key] as? T?
    }

    operator fun <T> set(key: String, value: T) {
        extra[key] = value
    }

    /**
     * Slice of the state used to compose reducers.
     */
    fun <U : Any> slice(
        getSlice: () -> U,
        setSlice: (U) -> Unit,
    ): ActionContext<U> {
        @Suppress("UNCHECKED_CAST")
        return ActionContext(
            store = store,
            action = action,
            extra = extra,
            getState = getSlice,
            setState = setSlice,
        )
    }
}
