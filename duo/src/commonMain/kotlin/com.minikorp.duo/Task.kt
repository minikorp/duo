package com.minikorp.duo

import kotlinx.coroutines.flow.Flow

/**
 * Simple wrapper to track ongoing tasks (network / database) and display it's progress
 * on the view.
 *
 * This class is [Parcelable] but won't write terminal states (Running) to avoid
 * UI inconsistencies.
 *
 * Currently in the Android module due to dependencies with [Parcelable]. Might
 * be promoted to the common module if kotlinx.serialization allows it.
 */
data class Task internal constructor(
    val state: State,
    val error: Throwable? = null
)  {

    enum class State {
        SUCCESS, FAILURE, IDLE, RUNNING
    }

    companion object {

//        @JvmField
//        @Suppress("UNUSED")
//        val CREATOR: Parcelable.Creator<Task> = object : Parcelable.Creator<Task> {
//            override fun createFromParcel(source: Parcel): Task {
//                val state = State.valueOf(source.readString()!!)
//                // try to recover the exception too
//                val hasSerializedError = source.readInt() != 0
//                val error = if (hasSerializedError) {
//                    source.readSerializable() as? Throwable?
//                } else {
//                    null
//                }
//
//                return Task(state, error)
//            }
//
//            override fun newArray(size: Int): Array<Task> {
//                return Array(size) { idle() }
//            }
//        }

        fun success(): Task = Task(State.SUCCESS)
        fun failure(error: Throwable? = null): Task = Task(State.FAILURE, error)
        fun running(): Task = Task(State.RUNNING)
        fun idle(): Task = Task(State.IDLE)
    }

//    override fun writeToParcel(dest: Parcel, flags: Int) {
//        if (state == State.RUNNING) { // This is not a terminal state, can't persist it
//            dest.writeString(State.IDLE.name)
//            return
//        }
//        dest.writeString(state.name)
//        if (error != null) {
//            dest.writeInt(1)
//            dest.writeSerializable(error)
//        } else {
//            dest.writeInt(0)
//        }
//    }

//    override fun describeContents(): Int = 0

    val isSuccess: Boolean get() = state == State.SUCCESS
    val isIdle: Boolean get() = state == State.IDLE
    val isFailure get() = state == State.FAILURE
    val isRunning: Boolean get() = state == State.RUNNING

    inline fun onSuccess(crossinline action: () -> Unit): Task {
        @Suppress("UNCHECKED_CAST")
        if (isSuccess) action()
        return this
    }

    inline fun onFailure(crossinline action: (error: Throwable?) -> Unit): Task {
        if (isFailure) action(error)
        return this
    }

    inline fun onRunning(crossinline action: () -> Unit): Task {
        @Suppress("UNCHECKED_CAST")
        if (this.isRunning) action()
        return this
    }

    inline fun onIdle(crossinline action: () -> Unit): Task {
        if (isIdle) action()
        return this
    }


    override fun toString(): String {
        return if (state != State.FAILURE) {
            state.toString()
        } else {
            "${state}(error=${error?.message}"
        }
    }
}

/** All tasks succeeded. */
fun Iterable<Task>.allSuccessful(): Boolean {
    return this.all { it.isSuccess }
}

/** Any tasks failed. */
fun Iterable<Task>.anyFailure(): Boolean {
    return this.any { it.isFailure }
}

/** Any task is running. */
fun Iterable<Task>.anyRunning(): Boolean {
    return this.any { it.isRunning }
}

/**
 * Run a side effect for every transition to success
 */
fun Flow<Task>.onEachSuccess(fn: suspend (task: Task) -> Unit): Flow<Task> {
    return onEachChange({ prev, next -> !prev.isSuccess && next.isSuccess }, fn)
}

/**
 * Run a side effect for every transition to failure
 */
fun Flow<Task>.onEachFailure(fn: suspend (task: Task) -> Unit): Flow<Task> {
    return onEachChange({ prev, next -> !prev.isFailure && next.isFailure }, fn)
}

/**
 * Run a side effect for every transition to loading
 */
fun Flow<Task>.onEachRunning(fn: suspend (task: Task) -> Unit): Flow<Task> {
    return onEachChange({ prev, next -> !prev.isRunning && next.isRunning }, fn)
}
