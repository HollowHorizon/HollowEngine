import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import ru.hollowhorizon.hollowengine.client.ui.Box
import ru.hollowhorizon.hollowengine.client.ui.Column
import ru.hollowhorizon.hollowengine.client.ui.HollowUiSurface
import ru.hollowhorizon.hollowengine.client.ui.LazyColumn
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.Row
import ru.hollowhorizon.hollowengine.client.ui.Text
import ru.hollowhorizon.hollowengine.client.ui.UiTextAlign
import ru.hollowhorizon.hollowengine.client.ui.percent
import ru.hollowhorizon.hollowengine.client.ui.px
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

class UiPerformanceBenchmarks {
    @Test
    fun `complex ui frame recomposes within nine millisecond median budget`() {
        val revision = mutableStateOf(0)

        HollowUiSurface().use { runtime ->
            runtime.setContent {
                ComplexUi(revision.value)
            }

            repeat(WarmupFrames) { frame ->
                revision.value = frame
                runtime.frame(FrameWidth, FrameHeight, nowMillis = frame.toLong())
            }

            val samples = LongArray(SampleFrames) { frame ->
                revision.value = frame + WarmupFrames
                measureNanoTime {
                    runtime.frame(FrameWidth, FrameHeight, nowMillis = (frame + WarmupFrames).toLong())
                }
            }.sorted()
            val medianMillis = samples[samples.size / 2].toDouble() / NanosPerMillisecond

            assertTrue(
                medianMillis <= RecompositionBudgetMillis,
                "Median complex UI frame took $medianMillis ms, budget is $RecompositionBudgetMillis ms",
            )
        }
    }

    @Composable
    private fun ComplexUi(revision: Int) {
        Column(
            modifier = Modifier.then(
                Modifier.size(FrameWidth.px, FrameHeight.px),
                Modifier.gap(4.px),
            ),
        ) {
            repeat(4) { group ->
                Row(
                    modifier = Modifier.then(
                        Modifier.size(100.percent, 46.px),
                        Modifier.gap(6.px),
                    ),
                ) {
                    repeat(5) { index ->
                        Box(
                            modifier = Modifier.then(
                                Modifier.size(100.percent, 38.px),
                                Modifier.grow(1f),
                            ),
                        ) {
                            Text(
                                "Tile $group:$index r$revision",
                                modifier = Modifier.then(
                                    Modifier.fontSize(9f),
                                    Modifier.textAlign(UiTextAlign.CENTER),
                                ),
                            )
                        }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.then(
                    Modifier.size(100.percent, 180.px),
                    Modifier.input(scrollable = true),
                    Modifier.gap(3.px),
                ),
            ) {
                repeat(120) { index ->
                    Row(
                        modifier = Modifier.then(
                            Modifier.size(100.percent, 18.px),
                            Modifier.gap(4.px),
                        ),
                    ) {
                        Text("Row $index", modifier = Modifier.then(Modifier.size(46.px, 16.px), Modifier.fontSize(8f)))
                        Text(
                            "revision=$revision value=${index * 17}",
                            modifier = Modifier.then(
                                Modifier.size(100.percent, 16.px),
                                Modifier.grow(1f),
                                Modifier.fontSize(8f),
                            ),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val FrameWidth = 360f
        const val FrameHeight = 280f
        const val WarmupFrames = 20
        const val SampleFrames = 40
        const val NanosPerMillisecond = 1_000_000.0
        const val RecompositionBudgetMillis = 9.0
    }
}
