package ru.hollowhorizon.hollowengine.client.gui.scripting.theme

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hc.client.kool.KoolManager.MONOCRAFT_DATA
import ru.hollowhorizon.hollowengine.client.gui.scripting.ideColors
import ru.hollowhorizon.hollowengine.client.gui.scripting.ideSizes
import kotlin.math.roundToInt

object ThemeEditor : Composable {
    val dockable = UiDockable("Theme Editor", floatingWidth = Dp(100f), floatingHeight = Dp(100f))
    lateinit var ideSurface: UiSurface
    lateinit var onRemove: () -> Unit

    private val colorEntries = listOf(
        ColorEntry("Primary", ideColors.primary),
        ColorEntry("Primary variant", ideColors.primaryVariant),
        ColorEntry("Secondary", ideColors.secondary),
        ColorEntry("Secondary variant", ideColors.secondaryVariant),
        ColorEntry("Background", ideColors.background),
        ColorEntry("Background variant", ideColors.backgroundVariant),
        ColorEntry("On primary", ideColors.onPrimary),
        ColorEntry("On secondary", ideColors.onSecondary),
        ColorEntry("On background", ideColors.onBackground)
    )
    private val selectedColor = mutableStateOf(0)

    private fun makeColors() = Colors(
        primary = colorEntries[0].color,
        primaryVariant = colorEntries[1].color,
        secondary = colorEntries[2].color,
        secondaryVariant = colorEntries[3].color,
        background = colorEntries[4].color,
        backgroundVariant = colorEntries[5].color,
        onPrimary = colorEntries[6].color,
        onSecondary = colorEntries[7].color,
        onBackground = colorEntries[8].color,
        isLight = colorEntries[4].color.brightness > 0.5f
    )

    override fun UiScope.compose() {
        ideColors = makeColors()
        val entry = colorEntries[selectedColor.use()]

        Column(Grow.Std, Grow.Std) {
            TitleBar(dockable, onCloseAction = { onRemove() })
            Text("${entry.name} color") {
                modifier
                    .margin(sizes.gap)
                    .font(sizes.largeText)
            }
            ColorChooserH(entry.hue, entry.sat, entry.value, entry.alpha, entry.hexString) {
                surface.triggerUpdate()
                surface.colors = makeColors()
                ideSurface.colors = surface.colors
            }
            LazyList(
                containerModifier = {
                    it
                        .margin(sizes.gap)
                        .size(Grow.Std, Grow.Std)
                }
            ) {
                var hoveredItem by remember(-1)
                itemsIndexed(colorEntries) { i, it ->
                    it.apply {
                        itemRow(i, i == hoveredItem).apply {
                            modifier.onEnter { hoveredItem = i }
                            modifier.onExit { hoveredItem = -1 }
                            modifier.onClick { selectedColor.set(i) }
                        }
                    }
                }
            }
        }
    }

    private class ColorEntry(val name: String, initColor: Color) {
        val hue = mutableStateOf(0f)
        val sat = mutableStateOf(1f)
        val value = mutableStateOf(1f)
        val alpha = mutableStateOf(1f)
        val hexString = mutableStateOf("")

        val color: Color get() = Color.Hsv(hue.value, sat.value, value.value).toSrgb(a = alpha.value)

        init {
            setColor(initColor)
        }

        fun setColor(color: Color) {
            val hsv = color.toHsv()
            hue.set(hsv.h)
            sat.set(hsv.s)
            value.set(hsv.v)
            alpha.set(color.a)
        }

        fun UiScope.itemRow(index: Int, isHovered: Boolean) = Row(Grow.Std) {
            if (isHovered) {
                modifier.backgroundColor(colors.secondaryAlpha(0.5f))
            } else if (index == selectedColor.use()) {
                modifier.backgroundColor(colors.secondaryAlpha(0.3f))
            } else if (index % 2 == 0) {
                val bg = if (colors.isLight) Color.BLACK.withAlpha(0.05f) else colors.secondaryAlpha(0.05f)
                modifier.backgroundColor(bg.withAlpha(0.05f))
            }

            val color = Color.Hsv(hue.use(), sat.use(), value.use()).toSrgb(a = alpha.use())
            Box(80.dp, 64.dp) {
                modifier
                    .backgroundColor(color)
                    .border(RectBorder(if (colors.isLight) Color.BLACK else Color.WHITE, 1.dp))
                    .margin(sizes.smallGap)
            }
            Column {
                modifier
                    .margin(start = sizes.largeGap)
                    .alignY(AlignmentY.Center)
                Text(name) {  }
                Row {
                    Text("#${color.toHexString()}") {
                        modifier.font(ideSizes.normalText)
                    }
                    Text("HSVA:") {
                        modifier.font(ideSizes.normalText)
                    }
                    Text("${hue.value.roundToInt()}") {
                        modifier.font(ideSizes.normalText).textAlignX(AlignmentX.End)
                    }
                    Text("${(sat.value * 100f).roundToInt()}") {
                        modifier.font(ideSizes.normalText).textAlignX(AlignmentX.End)
                    }
                    Text("${(value.value * 100f).roundToInt()}") {
                        modifier.font(ideSizes.normalText).textAlignX(AlignmentX.End)
                    }
                    Text("${(alpha.value * 100f).roundToInt()}") {
                        modifier.font(ideSizes.normalText).textAlignX(AlignmentX.End)
                    }
                }
            }
        }
    }
}