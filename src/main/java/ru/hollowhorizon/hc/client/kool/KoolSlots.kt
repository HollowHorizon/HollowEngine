package ru.hollowhorizon.hc.client.kool

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.Time
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.Container
import net.minecraft.world.item.TooltipFlag
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hc.client.kool.KoolManager.MONOCRAFT
import ru.hollowhorizon.hc.client.render.render
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hc.common.containers.ClientContainerManager

fun UiScope.DragStackTooltip() {
    val mouseX = doubleArrayOf(0.0)
    val mouseY = doubleArrayOf(0.0)
    GLFW.glfwGetCursorPos(Minecraft.getInstance().window.window, mouseX, mouseY)
    val player = Minecraft.getInstance().player ?: return
    val dragItem = ClientContainerManager.PLAYERS_HOLD_STACKS[player.uuid] ?: return
    if (!dragItem.isEmpty) Popup(mouseX[0].toFloat() - 32f, mouseY[0].toFloat() - 32f) {
        GlCanvas(null, {
            dragItem.render(x, y, width, height)
        }) {
            modifier.background(null).size(64.dp, 64.dp).zLayer(2000)

            Text(dragItem.count.toString()) {
                modifier.align(AlignmentX.End, AlignmentY.Bottom)
                    .font(MsdfFont(MONOCRAFT, 26f)).zLayer(2500)
            }
        }
        modifier.background(null)
    }
}

fun UiScope.Slot(container: Container, slotId: Int, slotSize: Dimension) = GlCanvas(null, {
    var size by remember { mutableStateOf(0f) }

    val hovered = mouseX in x..x + width && mouseY in y..y + height

    if (hovered) size += Time.deltaT * 5f
    else size -= Time.deltaT * 5f
    size = size.coerceIn(0f, 1f)

    container.getItem(slotId).render(x, y, width, height, 0.8f + 0.2f * size)
}) {
    val item = container.getItem(slotId)
    if (item.count > 0) Text(item.count.toString()) {
        modifier.align(AlignmentX.End, AlignmentY.Bottom)
            .font(MsdfFont(MONOCRAFT, 26f)).zLayer(400)
    }

    val state = this@Slot.remember { TooltipState(0.0) }
    if(!item.isEmpty) Tooltip(state) {
        modifier
            .margin(top = Dp.fromPx(state.pointerY.use()))
            .padding(10.dp)
            .layout(CellLayout)
            .background(UiRenderer { node ->
                node.apply {
                    getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                        .localRoundRect(0f, 0f, widthPx, heightPx, 15f, colors.backgroundVariant)
                    colors.primaryVariantAlpha(0.5f).let {
                        getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                            .localRoundRectBorder(0f, 0f, widthPx, heightPx, 15f, sizes.borderWidth.px*3f, it)
                    }
                }
            })
        Column {
            item.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.NORMAL).forEach {
                Row {
                    it.textLine().spans.forEach { text ->
                        Text(text.first) {
                            modifier.font(text.second.font)
                                .textColor(text.second.color)
                        }
                    }
                }
            }
        }
    }

    modifier.size(slotSize, slotSize)
        .zLayer(100)
        .onHover { pointer ->
            if (!pointer.isLeftClick && !pointer.isRightClick && !pointer.isLeftDoubleClick) return@onHover
            val player = Minecraft.getInstance().player ?: return@onHover
            ClientContainerManager.clickSlot(
                player,
                container,
                container,
                slotId,
                pointer.isLeftClick,
                pointer.isLeftDoubleClick,
                Screen.hasShiftDown()
            )
        }
}