package com.minikorp.duo

/**
 * Modify the state in place.
 */
class InlinedStateAction<S : Any>(
    val state: S,
    override val logConfig: ActionLogConfig = ActionLogConfig(
        actionName = "InlinedStateAction",
        logAction = false,
    )
) : Action, CustomLogAction




