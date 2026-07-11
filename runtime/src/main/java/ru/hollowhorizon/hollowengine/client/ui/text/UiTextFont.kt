package ru.hollowhorizon.hollowengine.client.ui.text

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hollowengine.client.ui.style.DefaultUiFontSize
import ru.hollowhorizon.hollowengine.client.ui.widgets.*
import java.util.concurrent.ConcurrentHashMap

private val minecraftFont: Font?
    get() = runCatching { Minecraft.getInstance()?.font }.getOrNull()

internal sealed interface UiTextFont {
    val signature: Int

    fun lineHeight(fontSize: Float): Float

    fun width(text: String, fontSize: Float, style: UiInlineStyle): Float

    fun advance(char: Char, fontSize: Float, style: UiInlineStyle): Float = width(char.toString(), fontSize, style)
}

internal object UiTextFonts {
    private const val EstimatedGlyphWidth = 6f
    private val resolvedFonts = ConcurrentHashMap<String, UiTextFont>()

    fun resolve(fontFamily: String?): UiTextFont {
        val normalized = fontFamily?.takeIf { it.isNotBlank() } ?: return VanillaTextFont
        return resolvedFonts.computeIfAbsent(normalized) { family ->
            UiMsdfFont.getMetrics(family)?.let { MsdfTextFont(family, it) } ?: VanillaTextFont
        }
    }

    fun signature(fontFamily: String?): Int = resolve(fontFamily).signature

    fun clearResolvedFonts() {
        resolvedFonts.clear()
    }

    private data object VanillaTextFont : UiTextFont {
        override val signature: Int
            get() {
                val activeFont = minecraftFont
                return 31 * System.identityHashCode(activeFont) + (activeFont?.lineHeight ?: DefaultUiFontSize.toInt())
            }

        override fun lineHeight(fontSize: Float): Float = fontSize

        override fun width(text: String, fontSize: Float, style: UiInlineStyle): Float {
            val activeFont = minecraftFont
            val width = if (activeFont == null) {
                val advance = estimatedAdvance(style)
                text.length * advance
            } else if (style.bold) {
                activeFont.width(text.boldComponent()).toFloat()
            } else {
                activeFont.width(text).toFloat()
            }
            return width * (fontSize / (activeFont?.lineHeight?.toFloat() ?: DefaultUiFontSize))
        }

        private fun estimatedAdvance(style: UiInlineStyle): Float = EstimatedGlyphWidth + if (style.bold) 1f else 0f
    }

    private data class MsdfTextFont(
        val family: String,
        val metrics: UiMsdfFontMetrics,
    ) : UiTextFont {
        override val signature: Int = 31 * family.hashCode() + System.identityHashCode(metrics)

        override fun lineHeight(fontSize: Float): Float = metrics.lineHeight(fontSize)

        override fun width(text: String, fontSize: Float, style: UiInlineStyle): Float {
            val boldOffset = if (style.bold) fontSize / 16f else 0f
            return metrics.width(text, fontSize) + text.length * boldOffset
        }

        override fun advance(char: Char, fontSize: Float, style: UiInlineStyle): Float {
            val boldOffset = if (style.bold) fontSize / 16f else 0f
            return metrics.advance(char, fontSize) + boldOffset
        }
    }
}

private fun String.boldComponent(): Component {
    return Component.literal(this).withStyle {
        it.withBold(true)
    }
}
