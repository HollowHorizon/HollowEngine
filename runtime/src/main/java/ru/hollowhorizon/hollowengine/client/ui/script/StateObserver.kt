package ru.hollowhorizon.hollowengine.client.ui.script

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
inline fun <reified T> observe(crossinline generator: () -> T): T? {
    return produceState<T?>(null) {
        while (true) {
            value = generator()
            delay(50.milliseconds)
        }
    }.value
}