package com.minikorp.duo

import kotlinx.datetime.Clock
import kotlin.coroutines.cancellation.CancellationException

private val defaultLogConfig = ActionLogConfig()

/** Counter for how many actions at depth 0 have been dispatched (root actions). */
private var rootActionCounter = 0

/** Counter for how many actions have been dispatched. */
private var actionCounter = 0

/**
 * Configuration for any action going through [LoggerMiddleware].
 */
data class ActionLogConfig(
    /** Level equivalent to android log levels, from 2 (verbose) to 7 (assert). */
    val logLevel: Int = LoggerMiddleware.DEBUG,
    /** Pretty name of the action. */
    val actionName: String? = null,
    /** Skip logger completely. */
    val silent: Boolean = false,
    /** Log the action itself. */
    val logAction: Boolean = true,
    /** Log the state changes the actions will produce. */
    val logState: Boolean = true,
    /** Box art for state changes */
    val stateDecorator: String = "├",
    /** Box art for state changes when state is multiline. */
    val stateMultilineDecorator: String = "│\\",
    /** Box art for unhandled exceptions. */
    val errorDecorator: String = "│ X ",
    /** The action will only have side effects. */
    val effectAction: Boolean = false,
    /** Box art for effects. */
    val effectDecorator: String = "»",
)

/**
 * Custom logging for prettier format for [LoggerMiddleware].
 *
 * @see [ActionLogConfig]
 */
interface CustomLogAction : Action {
    val logConfig: ActionLogConfig
}

//TODO: Multiplatform
///** Utility function to drop the package name from action qualified names. */
//internal fun extractClassName(clazz: KClass<*>): String {
//    return clazz.simpleName.substringAfterLast('.')
//}

/** Common writer for [LoggerMiddleware]. The function [log] will be called on the main thread. */
fun interface LoggerMiddlewareWriter {
    fun log(priority: Int, tag: String, message: String)
}

/** Default implementation of [LoggerMiddleware] that just prints. */
object PrintLoggerMiddlewareWriter : LoggerMiddlewareWriter {
    override fun log(priority: Int, tag: String, message: String) {
        println("${Clock.System.now()} - $tag: $message")
    }
}

/**
 * Action logging for stores.
 */
class LoggerMiddleware<S : Any>(
    var tag: String = "DuoLog",
    private val diffFunction: ((a: Any?, b: Any?) -> String) = { _, b -> b.toString() },
    private val writer: LoggerMiddlewareWriter = PrintLoggerMiddlewareWriter,
) : Middleware<S> {

    companion object {
        const val VERBOSE = 2
        const val DEBUG = 3
        const val INFO = 4
        const val WARN = 5
        const val ERROR = 6
        const val ASSERT = 7
    }

    private var ActionContext<*>.depth: Int
        get() = this["log_depth"] ?: 0
        set(value) {
            this["log_depth"] = value
        }

    private var ActionContext<*>.rootNumber: Int
        get() = this["log_rootNumber"] ?: 0
        set(value) {
            this["log_rootNumber"] = value
        }

    private val upCorner = "┌─"
    private val downCorner = "└>"
    private val verticalBar = "│ "

    fun writeWithContext(
        actionContext: ActionContext<*>,
        logLevel: Int,
        tag: String,
        message: String,
    ) {
        val (orderHint, indent) = calculateOrderHintAndIndent(actionContext)
        val prefix = "$orderHint $indent"
        writeLines(logLevel, tag, prefix, message)
    }

    private fun pad(actionNumber: Int): String {
        return (actionNumber % 100).toString().padStart(2, '0')
    }

    private fun writeLines(
        logLevel: Int,
        tag: String,
        prefix: String,
        message: String,
        firstLineDecorator: String = "",
        extraLinesDecorator: String = "  ",
    ) {
        message.lineSequence().filter { it.isNotBlank() }.forEachIndexed { index, s ->
            val indent = if (index == 0) firstLineDecorator else extraLinesDecorator
            writer.log(logLevel, tag, "$prefix$indent$s")
        }
    }

    private fun calculateOrderHintAndIndent(actionContext: ActionContext<*>): Pair<String, String> {
        val actionCount = actionCounter++
        val orderHint = "[${pad(actionContext.rootNumber)},${pad(actionCount)}]"
        val indent = verticalBar.repeat(actionContext.depth)
        return orderHint to indent
    }

    override suspend fun intercept(ctx: ActionContext<S>, chain: Chain<S>) {
        val action = ctx.action
        val depth = ctx.depth

        val logConfig = if (action is CustomLogAction) action.logConfig else defaultLogConfig
        val logLevel = logConfig.logLevel

        // Ordering
        if (depth == 0) {
            ctx.rootNumber = rootActionCounter++
        }

        val (orderHint, indent) = calculateOrderHintAndIndent(ctx)
        val prefix = "$orderHint $indent"
        val start = Clock.System.now().epochSeconds
        val actionName = logConfig.actionName ?: "Action" //TODO: Multiplatform extractClassName()

        try {
            if (logConfig.silent) {
                // Do nothing
                return chain.proceed(ctx)
            }

            if (logConfig.effectAction) {
                // Exit early, don't draw the full box
                writeLines(logLevel, tag, prefix, "${logConfig.effectDecorator} $action")
                return chain.proceed(ctx)
            }

            if (logConfig.logAction) {
                writeLines(logLevel, tag, prefix, "$upCorner $action")
            }

            // Pass it down
            ctx.depth++ // push
            val oldState = ctx.state
            chain.proceed(ctx)
            val processTime = (Clock.System.now().epochSeconds - start)
            val newState = ctx.state
            ctx.depth-- // pop

            if (logConfig.logState && oldState != newState) {
                val diff = diffFunction.invoke(oldState, newState)
                writeLines(
                    logLevel,
                    tag,
                    prefix,
                    " $diff",
                    logConfig.stateDecorator,
                    logConfig.stateMultilineDecorator,
                )
            }

            if (logConfig.logAction) {
                writer.log(
                    logLevel,
                    tag,
                    "$orderHint $indent$downCorner $actionName ${processTime}ms",
                )
            }
        } catch (ex: Throwable) {
            if (ex is CancellationException) {
                throw ex // Do nothing, this is expected
            }

            writeLines(
                ERROR,
                tag,
                prefix,
                ex.stackTraceToString(),
                firstLineDecorator = logConfig.errorDecorator,
                extraLinesDecorator = logConfig.errorDecorator,
            )

            val processTime = (Clock.System.now().epochSeconds - start)

            writer.log(
                ERROR,
                tag,
                "$orderHint $indent$downCorner $actionName ${processTime}ms",
            )
            throw ex
        }
    }
}

/**
 * Write a log inside the [LoggerMiddleware] box art contextualized to the current action.
 */
fun ActionContext<*>.log(message: String) {
    val logger = store.findMiddleware<LoggerMiddleware<*>>()
    logger?.writeWithContext(this, LoggerMiddleware.DEBUG, logger.tag, message)
}
