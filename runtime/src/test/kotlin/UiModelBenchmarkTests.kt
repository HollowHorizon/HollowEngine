import androidx.compose.runtime.mutableStateOf
import ru.hollowhorizon.hollowengine.client.ui.HollowComposeUiRuntime
import ru.hollowhorizon.hollowengine.client.ui.HollowUiRuntime
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlBuilder
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlContent
import ru.hollowhorizon.hollowengine.client.ui.xml.parseUiXml
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

class UiModelBenchmarkTests {
    @Test
    fun `benchmark legacy xml builder against compose ui model`() {
        val trees = listOf(
            benchmarkTree("Alpha", "0.25", "false"),
            benchmarkTree("Beta", "0.75", "true"),
        )
        val frames = 250

        @Suppress("DEPRECATION")
        val legacyNanos = measureNanoTime {
            val runtime = HollowUiRuntime()
            repeat(frames) { index ->
                val root = UiXmlBuilder().build(trees[index % trees.size])
                runtime.frame(root, 320f, 180f, nowMillis = index.toLong())
            }
        }

        val tree = mutableStateOf(trees.first())
        val composeNanos = measureNanoTime {
            HollowComposeUiRuntime().use { runtime ->
                runtime.setContent {
                    UiXmlContent(tree.value)
                }
                repeat(frames) { index ->
                    tree.value = trees[index % trees.size]
                    runtime.frame(320f, 180f, nowMillis = index.toLong())
                }
            }
        }

        println(
            "UI model benchmark ($frames frames): legacy=${legacyNanos.toMillis()}ms, " +
                    "compose=${composeNanos.toMillis()}ms",
        )
        assertTrue(legacyNanos > 0L)
        assertTrue(composeNanos > 0L)
    }

    private fun benchmarkTree(title: String, progress: String, checked: String) = parseUiXml(
        """
        <box id="root" layout="column" width="320px" height="180px" gap="6px" padding="8px">
            <text id="title" text="$title" />
            <box id="row" layout="row" gap="4px">
                <slider id="progress" value="$progress" min="0" max="1" width="120px" />
                <checkbox id="enabled" checked="$checked" />
            </box>
            <text-field id="field" value="Editable text" width="180px" />
            <box id="content" scrollable="true" width="220px" height="80px">
                <text id="message" text="Preview benchmark text" />
            </box>
        </box>
        """.trimIndent(),
    )

    private fun Long.toMillis(): Long = this / 1_000_000L
}
