package ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs

import com.mineinabyss.geary.datatypes.Component
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import kotlinx.serialization.KSerializer
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentHolder
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.EditorIcon
import ru.hollowhorizon.hollowengine.common.geary.components.GenericEditor
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.reflect.full.findAnnotation

class PrefabEditorFile(path: String, bytes: ByteArray) : ModelEditorFile(path) {
    private val components = mutableStateListOf<EditorComponent>()

    override fun save() {
        // TODO: Реализовать сохранение в .entity.prefab (JSON)
    }

    override fun UiScope.composeSidebar() {
        val componentPopup = remember { ItemPopupMenu<Unit>("prefab-component-popup") }

        ScrollArea(Grow.Std, Grow.Std, containerModifier = {
            it.backgroundColor(ColorTheme.UI.BackgroundSecondary)
                .margin(end = Dimensions.PaddingMedium)
        }, withHorizontalScrollbar = false, vScrollbarModifier = {
            it.colors(
                trackColor = ColorTheme.UI.BackgroundSecondary.withAlpha(0f),
                trackHoverColor = ColorTheme.UI.BackgroundElements,
                color = ColorTheme.UI.BackgroundAccent,
                hoverColor = ColorTheme.UI.WhiteReplacement
            ).width(Dimensions.PaddingMedium)
        }) {
            modifier.layout(ColumnLayout).width(Grow.Std)
                .margin(end = Dimensions.PaddingMedium)
            Editor()
        }

        Box(Grow.Std) {
            modifier
                .margin(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.Accents.Main, Dimensions.PaddingMedium))
                .onClick {
                    componentPopup.show(
                        Vec2f(it.screenPosition),
                        buildComponentMenu(componentPopup),
                        Unit
                    )
                }

            Row {
                modifier.alignX(AlignmentX.Center)

                Image(icons.ADD) {
                    modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                        .margin(Dimensions.PaddingMedium)
                        .align(AlignmentX.Center, AlignmentY.Center)
                }

                Text("Добавить компонент") {
                    modifier
                        .font(remember {
                            MsdfFont(ColorTheme.Fonts.MONOCRAFT, 16f)
                        })
                        .textColor(Color.WHITE)
                        .align(AlignmentX.Center, AlignmentY.Center)
                }
            }
        }
        componentPopup()
    }

    fun UiScope.Editor() {
        Text("Компоненты префаба") {
            modifier
                .font(remember {
                    MsdfFont(ColorTheme.Fonts.MONOCRAFT, 16f)
                })
                .textColor(Color.WHITE)
                .margin(Dimensions.PaddingNormal)
                .margin(top = Dimensions.PaddingMedium)
                .align(AlignmentX.Center, AlignmentY.Center)
        }

        components.forEach { component ->
            ComponentEditor(component)
        }
    }

    private fun addComponent(key: ResourceLocation) {
        val holder = ComponentRegistry.getOrNull(key) ?: return
        val component = holder.create()
        val state = mutableStateOf(component)

        if (key == "hollowengine:model".rl) {
            state.onChange { _, newValue ->
                val modelPath = (newValue as? ru.hollowhorizon.hollowengine.common.geary.components.Model)?.model
                if (modelPath != null) modelController.model.set(modelPath)
            }
            // Initial trigger
            val modelPath = (component as? ru.hollowhorizon.hollowengine.common.geary.components.Model)?.model
            if (modelPath != null) modelController.model.set(modelPath)
        }

        components += EditorComponent(key, holder, state)
    }

    private fun buildComponentMenu(menu: ItemPopupMenu<Unit>): SubMenuItem<Unit> = SubMenuItem("Компоненты") {
        val existing = components.map { it.key }.toSet()
        val available = ComponentRegistry.keys
            .filter { it !in existing }
            .sortedBy { it.toString() }

        if (available.isEmpty()) {
            item("Все компоненты добавлены") {}
        } else {
            available.forEach { key ->
                val holder = ComponentRegistry[key] ?: return@forEach
                val serializer = holder.serializer
                val displayName = serializer.descriptor.serialName
                val icon = holder.value.findAnnotation<EditorIcon>()?.icon?.rl
                item(displayName, icon) {
                    addComponent(key)
                    menu.hide()
                }
            }
        }
    }

    private fun UiScope.ComponentEditor(component: EditorComponent) {
        @Suppress("UNCHECKED_CAST")
        val state = component.state as MutableStateValue<Any>

        @Suppress("UNCHECKED_CAST")
        val serializer = component.holder.serializer as KSerializer<Any>
        GenericEditor(state, serializer) {
            components.remove(component)
        }
    }

    private data class EditorComponent(
        val key: ResourceLocation,
        val holder: ComponentHolder<*>,
        val state: MutableStateValue<Component>,
    )
}
