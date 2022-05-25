import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.minikorp.duo.*
import kotlinx.coroutines.*
import java.io.Serializable

@State
data class AppState(
    val main: MainState = MainState(),
)

@State
data class MainState(
    val number: Int = 0,
    val loading: Boolean = false,
    val deeplyNestedState: DeeplyNestedState = DeeplyNestedState(),
    val listOfNestedStates: List<DeeplyNestedState> = emptyList(),
) : Serializable

@State
data class DeeplyNestedState(
    val text: String = "0",
) : Serializable

data class IncrementAction(val count: Int = 1) : Action


class MainReducer : Reducer<MainState> {

    @TypedReducer.Fun
    suspend fun reduceIncrement(ctx: ActionContext<MainState>, action: IncrementAction) {
        ctx.mutableState = ctx.state.copy(
            loading = true
        )
        delay(1000)
        ctx.mutableState = ctx.state.mutate {
            number += action.count
        }
        ctx.mutableState = ctx.state.copy(
            loading = false
        )
    }

    @TypedReducer.Root
    override suspend fun reduce(ctx: ActionContext<MainState>) {
        reduceTyped(ctx)
    }
}

val store: Store<AppState> = Store(
    initialState = AppState(),
    storeScope = MainScope(),
    reducer = createAppStateReducer(
        main = MainReducer(),
        main_deeplyNestedState = {

        }
    )
).apply {
    addMiddleware(LoggerMiddleware())
}

@Composable
@Preview
fun App() {
    val state = store.states.select { it.main }.collectAsState().value
    MaterialTheme {
        Button(onClick = {
            store.offer(IncrementAction())
        }) {
            Text("Count: ${if (state.loading) "???" else state.number}")
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
