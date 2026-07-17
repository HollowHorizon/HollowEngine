package ru.hollowhorizon.hollowengine.client.gui.scripting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.ui.Column
import ru.hollowhorizon.hollowengine.client.ui.LazyColumn
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.Row
import ru.hollowhorizon.hollowengine.client.ui.Text
import ru.hollowhorizon.hollowengine.client.ui.UiProfiler
import ru.hollowhorizon.hollowengine.client.ui.grow
import ru.hollowhorizon.hollowengine.client.ui.input
import ru.hollowhorizon.hollowengine.client.ui.onClick
import ru.hollowhorizon.hollowengine.client.ui.percent
import ru.hollowhorizon.hollowengine.client.ui.scroll
import ru.hollowhorizon.hollowengine.client.ui.size

@Composable
internal fun HollowIdeUiProfilerPanel(profiler: UiProfiler) {
    DisposableEffect(profiler) {
        profiler.enabled = true
        onDispose { profiler.enabled = false }
    }
    val snapshot = profiler.snapshot
    Column(
        tags = listOf("ide-panel", "ui-profiler-panel"),
        modifier = Modifier.size(100.percent, 100.percent),
    ) {
        Row(tags = listOf("ui-profiler-toolbar")) {
            ProfilerButton(if (profiler.enabled) "Pause" else "Resume") {
                profiler.enabled = !profiler.enabled
            }
            ProfilerButton("Clear") { profiler.clear() }
            ProfilerButton("Copy") {
                Minecraft.getInstance().keyboardHandler.clipboard = snapshot.report
            }
        }
        Column(
            tags = listOf("ui-profiler-scroll"),
            modifier = Modifier.size(100.percent, 0.percent).grow(1f).scroll(vertical = true, horizontal = true),
        ) {
            Text(snapshot.report, tags = listOf("ui-profiler-report"))
        }
    }
}

@Composable
private fun ProfilerButton(label: String, action: () -> Unit) {
    Text(
        label,
        tags = listOf("ui-profiler-button"),
        modifier = Modifier.input(clickable = true, hoverable = true).onClick { event ->
            action()
            event.consume()
        },
    )
}
