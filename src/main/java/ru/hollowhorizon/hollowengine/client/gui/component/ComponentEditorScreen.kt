package ru.hollowhorizon.hollowengine.client.gui.component

import de.fabmax.kool.input.CursorShape
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_DOWN
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_RIGHT
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors
import ru.hollowhorizon.hollowengine.client.kool.*
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks

class ComponentEditorScreen(val provider: ComponentDispatcher) : KoolScreen() {
    private val entityPreviewWidth = mutableStateOf(Dp(200f))

    override fun Scene.setup() {
        addPanelSurface(IdeTheme.colors, IdeTheme.sizes) {
            modifier.layout(CellLayout)
            Row(Grow.Std, Grow.Std) {
                Entity(provider as LivingEntity) {
                    var rotateX by remember { mutableStateOf(0f) }
                    var rotateY by remember { mutableStateOf(0f) }
                    var offsetX by remember { mutableStateOf(0f) }
                    var offsetY by remember { mutableStateOf(0f) }
                    var scale by remember { mutableStateOf(1f) }

                    modifier.size(entityPreviewWidth.use(), Grow.Std)
                        .yaw(rotateX)
                        .pitch(rotateY)
                        .offset(Vec2f(offsetX, offsetY))
                        .scale(scale * 0.25f)
                        .headRotationModifierX(0f)
                        .headRotationModifierY(1f)
                        .onDrag {
                            if (it.pointer.isLeftButtonDown) {
                                rotateX -= it.pointer.delta.x / 10
                                rotateY += it.pointer.delta.y / 10

                                rotateX = rotateX % 360
                                rotateY = rotateY.coerceIn(-90f, 90f)
                            } else if (it.pointer.isRightButtonDown) {
                                scale
                                offsetX += it.pointer.delta.x
                                offsetY += it.pointer.delta.y
                            }
                        }
                        .onWheelY {
                            val factor = 1.1f
                            scale *= if (it.pointer.scroll.y > 0) factor else 1 / factor
                            scale = scale.coerceIn(0.01f, 5f)
                        }
                }
                Splitter()
                LazyColumn {
                    items(provider.`hollowcore$components`.keys.toList()) {
                        val component = provider.`hollowcore$components`[it] ?: return@items

                        Column(Grow.Std) {
                            val isCollapsed = remember { mutableStateOf(false) }

                            ComponentHeader(it, isCollapsed)
                            if (!isCollapsed.use()) {
                                ComponentEditor(component)
                            }
                        }
                    }
                }
            }
            provider.`hollowcore$components`.values.forEach { component ->
                component.properties.values.forEach { property ->
                    if (property.hasRenderer) with(property.renderer) {
                        editor(
                            component,
                            JavaHacks.forceCast(property)
                        )
                    }
                }
            }
        }
    }

    private fun UiScope.ComponentHeader(name: ResourceLocation, isCollapsed: MutableStateValue<Boolean>) {
        Row(Grow.Std) {
            modifier.padding(sizes.smallGap)
                .backgroundColor(colors.background.mulRgb(1.2f))

            Arrow {
                modifier.rotation(if (isCollapsed.use()) ROTATION_RIGHT else ROTATION_DOWN)
                    .size(20.dp, 20.dp)
                    .onClick { isCollapsed.set(!isCollapsed.value) }
                    .alignY(AlignmentY.Center)
                    .margin(horizontal = sizes.smallGap)
            }

            Image("hollowengine:textures/gui/icons/autocomplete_package.svg") {
                modifier.size(20.dp, 20.dp)
                    .alignY(AlignmentY.Center)
                    .margin(horizontal = sizes.smallGap)
            }

            Text(name.toString()) {
                modifier.textAlignY(AlignmentY.Center).alignY(AlignmentY.Center)
            }

            Box(Grow.Std) {}

            Image("hollowengine:textures/gui/icons/remove.png") {
                val hoverListener = hoverColors(0.5f, Color("AAFF5588"), Color.WHITE)
                modifier.tint(hoverListener)
                    .size(20.dp, 20.dp)
                    .alignY(AlignmentY.Center)
                    .margin(horizontal = sizes.smallGap)
            }
        }
    }

    fun UiScope.ComponentEditor(component: Component<*>) {
        component.properties.asSequence().filter { it.value.hasRenderer }.forEach { (name, prop) ->
            val renderer = prop.renderer
            Row(Grow.Std) {
                modifier.padding(sizes.smallGap)
                Text("$name: ") {
                    modifier.alignY(AlignmentY.Center)
                }
                with(renderer) {
                    render(component, JavaHacks.forceCast(prop))
                }
            }
        }
    }

    private fun UiScope.Splitter() {
        val isSplitterHovered = remember(false)
        val dragStartWidth = remember(0f)
        Box(width = sizes.borderWidth * 2f, height = Grow.Std) {
            modifier
                .onEnter { isSplitterHovered.value = true }
                .onExit { isSplitterHovered.value = false }
                .onHover { PointerInput.cursorShape = CursorShape.RESIZE_EW }
                .onDragStart {
                    dragStartWidth.value = entityPreviewWidth.value.px
                }
                .onDrag {
                    val newWidthPx = dragStartWidth.value + it.pointer.dragMovement.x
                    val clampedPx = newWidthPx.coerceAtLeast(50.dp.px)
                    entityPreviewWidth.set(Dp.fromPx(clampedPx))
                }
                .backgroundColor(Color.BLACK.withAlpha(0.0001f))

            Box(width = sizes.borderWidth, height = Grow.Std) {
                modifier
                    .alignX(AlignmentX.Center)
                    .backgroundColor(if (isSplitterHovered.use()) colors.primary else IdeTheme.colors.secondaryVariant)
            }
        }
    }
}