package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import de.fabmax.kool.editor.ui.hoverBg
import de.fabmax.kool.editor.ui.lineHeight
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hollowengine.client.gui.scripting.createTexture

val COMPLETE_CLASS by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_class.png".rl, 16, 16) }
val COMPLETE_METHOD by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_method.png".rl, 16, 16) }
val COMPLETE_PACKAGE by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_package.png".rl, 16, 16) }
val COMPLETE_UNKNOWN by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_unknown.png".rl, 16, 16) }
val COMPLETE_VARIABLE by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_variable.png".rl, 16, 16) }

data class CompletionVariant(
    val text: String,
    val displayText: String,
    val tail: String,
    val icon: Icon,
) : Composable {
    override fun toString() = displayText

    override fun UiScope.compose() {
        Row(Grow.Std) {
            var isHovered by remember { mutableStateOf(false) }

            modifier.margin(sizes.smallGap)
            modifier.onEnter { isHovered = true }.onExit { isHovered = false }

            if(isHovered) {
                modifier.background(RoundRectBackground(colors.hoverBg, sizes.gap))
            }

            Image {
                modifier.image(
                    when (icon) {
                        Icon.PACKAGE -> COMPLETE_PACKAGE
                        Icon.METHOD -> COMPLETE_METHOD
                        Icon.VARIABLE -> COMPLETE_VARIABLE
                        Icon.CLASS -> COMPLETE_CLASS
                        Icon.UNKNOWN -> COMPLETE_UNKNOWN
                    }
                ).alignY(AlignmentY.Center).size(sizes.lineHeight, sizes.lineHeight).margin(horizontal=sizes.smallGap)
            }
            Text(displayText) {
                modifier.align(AlignmentX.Start, AlignmentY.Center).margin(horizontal=sizes.smallGap)
            }

            Box {
                modifier.width(Grow.Std)

                Text(tail) {
                    modifier.align(AlignmentX.End, AlignmentY.Center)
                        .margin(horizontal = sizes.smallGap)
                        .textColor(Color.LIGHT_GRAY)
                }
            }
        }
    }

    enum class Icon {
        PACKAGE, CLASS, METHOD, VARIABLE, UNKNOWN
    }
}

class OnCompletionsEvent(val fileName: String, val completions: List<CompletionVariant>) : Event