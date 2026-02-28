package ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs

import com.mineinabyss.geary.datatypes.Component
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentHolder
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.EditorIcon
import ru.hollowhorizon.hollowengine.common.geary.components.GenericEditor
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.common.utils.yaml.YamlFormat
import kotlin.reflect.full.findAnnotation

class PrefabEditorFile(path: String, bytes: ByteArray) : ModelEditorFile(path) {
    private val components = mutableStateListOf<EditorComponent>()

    private val prefabSerializersModule: SerializersModule = SerializersModule {
        polymorphic(Any::class) {
            ComponentRegistry.map { it.value }.forEach { holder ->
                @Suppress("UNCHECKED_CAST")
                subclass(holder.value as kotlin.reflect.KClass<Any>, holder.serializer as KSerializer<Any>)
            }
        }
    }

    @Serializable
    private data class PrefabYaml(
        val components: Map<String, String> = emptyMap(),
        val prefabs: Set<com.mineinabyss.geary.prefabs.PrefabKey> = emptySet(),
    )

    init {
        if (bytes.isNotEmpty()) {
            runCatching {
                val yaml = bytes.toString(Charsets.UTF_8)
                val prefab = YamlFormat.withModule(prefabSerializersModule)
                    .decodeFromString(PrefabYaml.serializer(), yaml)

                prefab.components.forEach { (keyString, data) ->
                    val key = keyString.rl
                    val holder = ComponentRegistry.getOrNull(key) ?: return@forEach

                    @Suppress("UNCHECKED_CAST")
                    val serializer = holder.serializer as KSerializer<Any>
                    val decoded = YamlFormat.decodeFromString(serializer, data, prefabSerializersModule)

                    @Suppress("UNCHECKED_CAST")
                    val state = mutableStateOf(decoded as Component)
                    hookModelPreview(key, state)
                    components += EditorComponent(key, holder, state)
                }
            }
        }
    }

    override fun save() {
        val file = filePath.fromReadablePath()
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }

        val prefab = PrefabYaml(
            components = run {
                components.associate { ec ->
                    val component = ec.state.value
                    val key = ec.key.toString()

                    @Suppress("UNCHECKED_CAST")
                    val serializer = ec.holder.serializer as KSerializer<Any>
                    key to YamlFormat.encodeToString(serializer, component, prefabSerializersModule)
                }
            }
        )

        val text = YamlFormat.withModule(prefabSerializersModule)
            .encodeToString(PrefabYaml.serializer(), prefab)
        file.writeText(text)
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
        val holder = ComponentRegistry.getOrNull(key) ?: return
        val component = holder.create()
        val state = mutableStateOf(component)

        hookModelPreview(key, state)

        components += EditorComponent(key, holder, state)
    }

    private fun hookModelPreview(key: ResourceLocation, state: MutableStateValue<Component>) {
        if (key == "hollowengine:model".rl) {
            state.onChange { _, newValue ->
                val modelPath = (newValue as? ru.hollowhorizon.hollowengine.common.geary.components.Model)?.model
                if (modelPath != null) modelController.model.set(modelPath)
            }
            val modelPath = (state.value as? ru.hollowhorizon.hollowengine.common.geary.components.Model)?.model
            if (modelPath != null) modelController.model.set(modelPath)
        }
    }

    private fun buildComponentMenu(menu: ItemPopupMenu<Unit>): SubMenuItem<Unit> = SubMenuItem("hollowengine.gui.entity_editor.components".lang) {
        val existing = components.map { it.key }.toSet()
        val available = ComponentRegistry.keys
            .filter { it !in existing }
            .sortedBy { it.toString() }

        if (available.isEmpty()) {
            item("hollowengine.gui.prefab_editor.all_components_added".lang) {}
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
