package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.*
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.text.*
import java.util.*

/**
 * The item's own tooltip on hover: the lines the game builds for it, mods' additions included.
 */
@Composable
fun Modifier.itemTooltip(stack: () -> ItemStack?): Modifier {
    var lines by remember { mutableStateOf<List<Component>>(emptyList()) }
    return onEnter {
        lines = stack()?.takeUnless(ItemStack::isEmpty)?.let(::itemTooltipLines).orEmpty()
    }.tooltipOnHover(enabled = lines.isNotEmpty(), tags = listOf("ui-tooltip", "item")) {
        Column(tags = listOf("ui-item-tooltip")) {
            lines.forEach { line -> TooltipLine(line) }
        }
    }
}

@Composable
private fun TooltipLine(line: Component) {
    val segments = remember(line) { line.segments() }
    Text(tags = listOf("ui-item-tooltip-line")) {
        segments.forEach { (style, text) -> Span(text, modifier = style.toModifier()) }
    }
}

private fun itemTooltipLines(stack: ItemStack): List<Component> {
    val minecraft = Minecraft.getInstance()
    val flag = if (minecraft.options.advancedItemTooltips) TooltipFlag.Default.ADVANCED else TooltipFlag.Default.NORMAL
    return runCatching {
        stack.getTooltipLines(
            Item.TooltipContext.of(minecraft.level), minecraft.player, flag
        )
    }.getOrElse { listOf(stack.hoverName) }
}

private fun Component.segments(): List<Pair<Style, String>> {
    val segments = ArrayList<Pair<Style, String>>()
    visit<Unit>({ style, text ->
        if (text.isNotEmpty()) segments += style to text
        Optional.empty()
    }, Style.EMPTY)
    return segments
}

private fun Style.toModifier(): Modifier? {
    val effects = buildList {
        color?.let { add(TextColor(UiColor.fromArgb(OpaqueAlpha or it.value))) }
        if (isBold) add(Bold())
        if (isItalic) add(Italic())
        if (isUnderlined) add(Underline())
        if (isStrikethrough) add(Strikethrough())
    }
    return if (effects.isEmpty()) null else Modifier.textEffects(*effects.toTypedArray())
}

private const val OpaqueAlpha = 0xFF shl 24
