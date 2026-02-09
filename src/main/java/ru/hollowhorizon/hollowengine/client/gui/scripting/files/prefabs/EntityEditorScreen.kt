package ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs

import com.mineinabyss.geary.datatypes.Component
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import kotlinx.serialization.KSerializer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.kool.KoolScreen
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.common.geary.api.entity
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentHolder
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.EditorIcon
import ru.hollowhorizon.hollowengine.common.geary.components.GenericEditor
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.reflect.full.findAnnotation

class EntityEditorScreen(val target: Entity) : KoolScreen() {
    private val modelController = ModelController()
    private val gearyEntity = target.entity
    private val components = mutableStateListOf<EditorComponent>()

    init {
        // Загружаем текущие компоненты сущности
        ComponentRegistry.forEach { holder ->
            val component = gearyEntity.get(holder.value.value)
            if (component != null) {
                val state = mutableStateOf(component as Component)
                // Привязка модели
                if (holder.value.serializer.descriptor.serialName == "hollowengine:model") {
                    state.onChange { _, newValue ->
                        val modelPath = (newValue as? ru.hollowhorizon.hollowengine.common.geary.components.Model)?.model
                        if (modelPath != null) modelController.model.set(modelPath)
                    }
                    val modelPath = (component as? ru.hollowhorizon.hollowengine.common.geary.components.Model)?.model
                    if (modelPath != null) modelController.model.set(modelPath)
                }
                
                state.onChange { _, newValue ->
                    gearyEntity.set(newValue, newValue::class)
                }
                
                components += EditorComponent(holder.value.serializer.descriptor.serialName.rl, holder.value, state)
            }
        }
    }

    override fun Scene.setup() {
        addPanelSurface(IdeTheme.colors, IdeTheme.sizes) {
            modifier.layout(CellLayout).backgroundColor(ColorTheme.UI.BackgroundGeneral.withAlpha(0.75f))
            Row(Grow.Std, Grow.Std) {
                Box(Grow(0.66f), Grow.Std) {
                    modelController()
                    Text("Редактирование сущности: ${target.displayName?.string}") {
                        modifier
                            .font(remember {
                                MsdfFont(ColorTheme.Fonts.MONOCRAFT, 24f, weight = MsdfFont.WEIGHT_BOLD)
                            })
                            .margin(Dimensions.PaddingMedium)
                            .zLayer(1000)
                            .align(AlignmentX.Center, AlignmentY.Top)
                    }
                }
                
                Column(Grow(0.33f), Grow.Std) {
                    modifier.backgroundColor(ColorTheme.UI.BackgroundSecondary)
                    composeSidebar()
                }
            }
        }
    }

    private fun UiScope.composeSidebar() {
        val componentPopup = remember { ItemPopupMenu<Unit>("entity-component-popup") }

        ScrollArea(Grow.Std, Grow.Std, containerModifier = {
            it.backgroundColor(null)
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
            
            Text("Компоненты") {
                modifier.font(remember { MsdfFont(ColorTheme.Fonts.MONOCRAFT, 16f) })
                    .textColor(Color.WHITE)
                    .margin(Dimensions.PaddingMedium)
                    .alignX(AlignmentX.Center)
            }

            components.forEach { component ->
                ComponentEditor(component)
            }
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
                    modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge).margin(Dimensions.PaddingMedium)
                }
                Text("Добавить компонент") {
                    modifier.alignY(AlignmentY.Center).textColor(Color.WHITE)
                }
            }
        }
        componentPopup()
    }

    private fun addComponent(key: ResourceLocation) {
        val holder = ComponentRegistry[key] ?: return
        val component = holder.create()
        gearyEntity.set(component, holder.value)
        
        val state = mutableStateOf(component)
        state.onChange { _, newValue ->
            gearyEntity.set(newValue, newValue::class)
        }
        
        if (key == "hollowengine:model".rl) {
            state.onChange { _, newValue ->
                val modelPath = (newValue as? ru.hollowhorizon.hollowengine.common.geary.components.Model)?.model
                if (modelPath != null) modelController.model.set(modelPath)
            }
            val modelPath = (component as? ru.hollowhorizon.hollowengine.common.geary.components.Model)?.model
            if (modelPath != null) modelController.model.set(modelPath)
        }

        components += EditorComponent(key, holder, state)
    }

    private fun buildComponentMenu(menu: ItemPopupMenu<Unit>): SubMenuItem<Unit> = SubMenuItem("Компоненты") {
        val existing = components.map { it.key }.toSet()
        val available = ComponentRegistry.keys.filter { it !in existing }.sortedBy { it.toString() }

        available.forEach { key ->
            val holder = ComponentRegistry[key] ?: return@forEach
            val icon = holder.value.findAnnotation<EditorIcon>()?.icon?.rl
            item(holder.serializer.descriptor.serialName, icon) {
                addComponent(key)
                menu.hide()
            }
        }
    }

    private fun UiScope.ComponentEditor(component: EditorComponent) {
        @Suppress("UNCHECKED_CAST")
        val state = component.state as MutableStateValue<Any>
        @Suppress("UNCHECKED_CAST")
        val serializer = component.holder.serializer as KSerializer<Any>
        GenericEditor(state, serializer) {
            gearyEntity.remove(component.holder.value)
            components.remove(component)
        }
    }

    private data class EditorComponent(
        val key: ResourceLocation,
        val holder: ComponentHolder<*>,
        val state: MutableStateValue<Component>,
    )
}
