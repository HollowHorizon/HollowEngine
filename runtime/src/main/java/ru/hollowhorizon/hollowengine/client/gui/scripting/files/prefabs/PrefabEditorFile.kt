package ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs

import com.mineinabyss.geary.datatypes.Component
import com.mineinabyss.geary.prefabs.PrefabKey
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
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentHolder
import ru.hollowhorizon.hollowengine.common.geary.components.EditorIcon
import ru.hollowhorizon.hollowengine.common.geary.components.GenericEditor
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentSchemaRegistry
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.reflect.full.findAnnotation

class PrefabEditorFile(path: String, bytes: ByteArray) : ModelEditorFile(path) {
    private val components = mutableStateListOf<EditorComponent>()
    private val hiddenComponents = mutableListOf<Component>()
    private var prefabRefs: Set<PrefabKey> = emptySet()

    init {
        if (bytes.isNotEmpty()) {
            runCatching {
                val prefab = EntitySerialization.deserializeFromYaml(bytes.toString(Charsets.UTF_8))
                prefabRefs = prefab.prefabRefs

                prefab.components.forEach { component ->
                    val descriptor = EntitySerialization.descriptorFor(component) ?: return@forEach
                    if (!descriptor.editable) {
                        hiddenComponents += component
                        return@forEach
                    }
                    val state = mutableStateOf(component)
                    hookModelPreview(state)
                    components += EditorComponent(descriptor.id, descriptor, state)
                }
                refreshModelPreview()
            }
        } else {
            clearModelPreview()
        }
    }

    override fun save() {
        val file = filePath.fromReadablePath()
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }

        val prefab = EntitySnapshot(
            prefabRefs = prefabRefs,
            components = components.map { it.state.value } + hiddenComponents
        )
        file.writeText(EntitySerialization.serializeToYaml(prefab))
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

                Text("hollowengine.gui.prefab_editor.add_component".lang) {
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
        Text("hollowengine.gui.prefab_editor.prefab_components".lang) {
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
        val holder = ComponentDescriptorRegistry.getOrNull(key) ?: return
        if (!holder.editable) return
        val component = holder.create()
        val state = mutableStateOf(component)

        hookModelPreview(state)

        components += EditorComponent(key, holder, state)
        refreshModelPreview()
    }

    private fun hookModelPreview(state: MutableStateValue<Component>) {
        state.onChange { _, _ -> refreshModelPreview() }
    }

    private fun refreshModelPreview() {
        val modelPath = components.firstNotNullOfOrNull { editor ->
            (editor.state.value as? Model)?.model
        }
        if (modelPath != null) modelController.setModel(modelPath) else clearModelPreview()
    }

    private fun clearModelPreview() {
        modelController.clearModel()
    }

    private fun buildComponentMenu(menu: ItemPopupMenu<Unit>): SubMenuItem<Unit> = SubMenuItem("hollowengine.gui.entity_editor.components".lang) {
        val existing = components.map { it.key }.toSet()
        val available = ComponentDescriptorRegistry
            .map { it.value }
            .filter { it.editable && it.id !in existing }
            .sortedBy { it.id.toString() }

        if (available.isEmpty()) {
            item("hollowengine.gui.prefab_editor.all_components_added".lang) {}
        } else {
            available
                .groupBy { it.id.namespace }
                .toSortedMap()
                .forEach { (namespace, descriptors) ->
                    subMenu(namespace) {
                        descriptors.forEach { descriptor ->
                            val schema = ComponentSchemaRegistry.descriptorSchema(descriptor.id)
                            val icon = (schema?.icon ?: descriptor.value.findAnnotation<EditorIcon>()?.icon)?.rl
                            val label = buildString {
                                append(schema?.displayName ?: descriptor.serializer.descriptor.serialName)
                                append(" [")
                                append(descriptor.id)
                                append(']')
                            }
                            item(label, icon) {
                                addComponent(descriptor.id)
                                menu.hide()
                            }
                        }
                    }
                }
        }
    }

    private fun UiScope.ComponentEditor(component: EditorComponent) {
        @Suppress("UNCHECKED_CAST")
        val state = component.state

        @Suppress("UNCHECKED_CAST")
        val serializer = component.holder.serializer as KSerializer<Any>
        GenericEditor(state, serializer) {
            components.remove(component)
            refreshModelPreview()
        }
    }

    private data class EditorComponent(
        val key: ResourceLocation,
        val holder: ComponentHolder<*>,
        val state: MutableStateValue<Component>,
    )
}
