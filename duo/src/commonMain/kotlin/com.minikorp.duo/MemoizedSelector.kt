package com.minikorp.duo

class MemoizedSelector<S : Any, T>(
    private val inputs: Array<(S) -> Any?>,
    private val output: (args: Array<Any?>) -> T
) {

    companion object {
        private object NoValue
    }

    private val lastInputValues = Array<Any?>(inputs.size) { NoValue }
    private var memoizedValue: Any? = NoValue

    fun invoke(state: S): T {
        var changed = false

        inputs.forEachIndexed { index, function ->
            val oldInput = lastInputValues[index]
            val newInput = function(state)
            changed = changed || oldInput != newInput
            lastInputValues[index] = newInput
        }

        if (changed || memoizedValue == NoValue) {
            memoizedValue = output(lastInputValues)
        }

        @Suppress("UNCHECKED_CAST")
        return memoizedValue as T
    }
}
