package ru.hollowhorizon.hollowengine.client.gui.component

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.Scene
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.kool.*
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks

class ComponentEditorScreen(val provider: ComponentDispatcher) : KoolScreen() {
    override fun Scene.setup() {
        addPanelSurface(IdeTheme.colors, IdeTheme.sizes) {
            Row(Grow.Std, Grow.Std) {
                Entity(provider as LivingEntity) {
                    var rotateX by remember { mutableStateOf(0f) }
                    var rotateY by remember { mutableStateOf(0f) }
                    var offsetX by remember { mutableStateOf(0f) }
                    var offsetY by remember { mutableStateOf(0f) }
                    var scale by remember { mutableStateOf(1f) }

                    modifier.size(Grow(0.5f), Grow.Std)
                        .yaw(rotateX)
                        .pitch(rotateY)
                        .offset(Vec2f(offsetX, offsetY))
                        .scale(scale)
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
                LazyColumn {
                    items(provider.`hollowcore$components`.keys.toList()) {

                        Column {
                            Text(it.toString()) {}
                            divider()
                            val component = provider.`hollowcore$components`[it] ?: return@Column
                            ComponentEditor(component)
                        }

                    }
                }
            }
        }
    }

    fun UiScope.ComponentEditor(component: Component<*>) {
        component.properties.forEach { name, prop ->
            val renderer = prop.renderer ?: return@forEach
            Row {
                Text("$name: ") {}
                with(renderer) {
                    render(component, JavaHacks.forceCast(prop))
                }
            }
        }
    }
}