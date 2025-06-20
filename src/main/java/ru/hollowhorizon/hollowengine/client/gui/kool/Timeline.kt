package ru.hollowhorizon.hollowengine.client.gui.kool

import de.fabmax.kool.KoolApplication
import de.fabmax.kool.KoolConfigJvm
import de.fabmax.kool.addScene
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_DOWN
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.DebugOverlay
import de.fabmax.kool.util.debugOverlay
import kotlin.random.Random

val objects = (0..100).map { Color(Random.nextFloat(), Random.nextFloat(), Random.nextFloat(), 1f) }.toMutableList()

fun UiScope.Timeline(position: Int, count: Int = 100) {
    val state = rememberListState()
    val scrollPos = state.xScrollDp.use() - 1000 + state.avgItemSizeDp * state.itemsFrom.use()
    var distance by remember(1f)
    Box(Grow.Std) {
        LazyRow(
            state = state,
            withHorizontalScrollbar = false
        ) {
            modifier.onWheelY {
                state.scrollDpX(it.pointer.scroll.y * -60f)
            }
            modifier.onDrag {
                val delta = it.pointer.delta
                if (delta.x != 0f) {
                    state.scrollDpX(Dp.fromPx(-delta.x).value)
                }
            }

            items((0..count).toList()) {
                Section(it, distance)
            }
        }
        var position by remember(0f)
        var isHovered by remember(false)
        Triangle {
            modifier.size(sizes.gap, sizes.gap)
                .onEnter { isHovered = true }.onExit { isHovered = false }
                .rotation(ROTATION_DOWN)
                .margin(start = Dp.fromPx(position) - Dp(scrollPos))
                .onDrag {
                    position = it.pointer.pos.x + Dp(scrollPos).px - uiNode.widthPx
                    position = position.coerceAtLeast(0f)
                }
        }
        Box {
            modifier.margin(start = sizes.gap * 0.5f + Dp.fromPx(position) - Dp(scrollPos) - sizes.borderWidth)
                .onEnter { isHovered = true }.onExit { isHovered = false }
                .size(sizes.borderWidth + Dp.fromPx(2f), Grow.Std)
                .onDrag {
                    position = it.pointer.pos.x + Dp(scrollPos).px - uiNode.widthPx
                    position = position.coerceAtLeast(0f)
                }
            if (isHovered) modifier.backgroundColor(colors.primary)
            else modifier.backgroundColor(colors.primaryVariant)
        }
    }
    Box {
        modifier.height(sizes.largeGap)
    }
    Slider {
        modifier.value(distance)
            .onChange { distance = it }
            .minValue(1f).maxValue(10f)
            .width(Grow.Std)
    }
}

fun UiScope.Section(id: Int, distance: Float) {
    Column {
        modifier.size(FitContent, 25.dp)

        Row(height = Grow.Std) {
            val d = if(distance > 4) sizes.smallGap * distance * 0.5f else sizes.smallGap * distance

            Box {
                modifier.margin(start = if(id == 0) sizes.smallGap * distance else d)
                    .size(sizes.borderWidth * 2, Grow.Std)
                    .backgroundColor(Color.WHITE)
            }
            repeat(if(distance > 4) 19 else 9) {
                Box {
                    modifier.margin(start = d)
                        .size(sizes.borderWidth, Grow(if ((it+1) % 5 == 0) 0.75f else 0.5f))
                        .backgroundColor(Color.WHITE)
                }
            }
        }

        Box(Grow.Std) {
            val d = if(distance > 4 && id != 0) sizes.smallGap * distance * 0.5f else sizes.smallGap * distance

            Text(id.toString()) {
                modifier.textAlignX(AlignmentX.Center)
                    .margin(start = d)
            }
        }
    }
}

fun main() =
    KoolApplication(
        KoolConfigJvm(
            renderBackend = KoolConfigJvm.Backend.OPEN_GL,
            maxFrameRate = 500,
            windowSize = Vec2i(756, 512)
        )
    ) {
        addScene {
            setupUiScene()

            addPanelSurface {
                Timeline(3)
            }
        }
        ctx.addScene(debugOverlay(DebugOverlay.Position.LOWER_RIGHT))
    }