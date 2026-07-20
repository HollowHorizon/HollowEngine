package ru.hollowhorizon.hollowengine.client.ui.render

import ru.hollowhorizon.hollowengine.client.ui.BeginLayerCommand
import ru.hollowhorizon.hollowengine.client.ui.EndLayerCommand
import ru.hollowhorizon.hollowengine.client.ui.UiRenderCommand

/** Top-level framebuffer layer ranges in a collected UI command stream. */
internal class UiLayerRenderPlan private constructor(
    val commands: List<UiRenderCommand>,
    val layers: List<UiLayerCommandRange>,
) {
    companion object {
        fun create(commands: List<UiRenderCommand>): UiLayerRenderPlan {
            val layers = ArrayList<UiLayerCommandRange>()
            var depth = 0
            var layerStart = -1
            var layerCommand: BeginLayerCommand? = null

            for (index in commands.indices) {
                when (val command = commands[index]) {
                    is BeginLayerCommand -> {
                        if (depth == 0) {
                            layerStart = index
                            layerCommand = command
                        }
                        depth++
                    }

                    is EndLayerCommand -> {
                        check(depth > 0) { "Framebuffer layer ended without a matching begin command" }
                        depth--
                        if (depth == 0) {
                            layers += UiLayerCommandRange(
                                startIndex = layerStart,
                                endIndex = index,
                                command = checkNotNull(layerCommand),
                            )
                            layerStart = -1
                            layerCommand = null
                        }
                    }

                    else -> Unit
                }
            }
            check(depth == 0) { "Framebuffer layer command stream is unbalanced" }
            return UiLayerRenderPlan(commands, layers)
        }
    }
}

internal data class UiLayerCommandRange(
    val startIndex: Int,
    val endIndex: Int,
    val command: BeginLayerCommand,
)
