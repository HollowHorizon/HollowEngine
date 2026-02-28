package ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.EditorFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar.ComboBox
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.items.dynamic.ItemPrefab
import ru.hollowhorizon.hollowengine.common.utils.json.JsonFormat
import ru.hollowhorizon.hollowengine.common.utils.yaml.YamlFormat

class ItemPrefabEditorFile(path: String, bytes: ByteArray) : EditorFile(path) {
    private val id = mutableStateOf("")
    private val maxStack = mutableStateOf("")
    private val maxDamage = mutableStateOf("")
    private val rarity = mutableStateOf("")
    private val fireResistant = mutableStateOf(false)
    private val tab = mutableStateOf("")
    private val tabIndex = mutableStateOf(-1)
    private val customTab = mutableStateOf("")
    private var cachedTabOptions: List<String> = emptyList()

    private val model = mutableStateOf("")
    private val modelParent = mutableStateOf("")
    private val modelTexture = mutableStateOf("")
    private val modelJson = mutableStateOf("")

    private val templateIndex = mutableStateOf(-1)
    private val templateOptions = listOf("generated", "handheld")

    private val isJson = path.endsWith(".json")

    init {
        if (bytes.isNotEmpty()) {
            runCatching {
                val text = bytes.toString(Charsets.UTF_8)
                val prefab = if (isJson) {
                    JsonFormat.decodeFromString<ItemPrefab>(text)
                } else {
                    YamlFormat.decodeFromString(ItemPrefab.serializer(), text)
                }
                applyPrefab(prefab)
            }
        }
    }

    override fun save() {
        val prefab = buildPrefab()
        val file = filePath.fromReadablePath()
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }

        val text = if (isJson) {
            JsonFormat.encodeToString(JsonFormat.serialize(ItemPrefab.serializer(), prefab))
        } else {
            YamlFormat.encodeToString(ItemPrefab.serializer(), prefab)
        }
        file.writeText(text)
    }

    override fun UiScope.compose() {
        modifier.backgroundColor(ColorTheme.UI.BackgroundGeneral)

        Row(Grow.Std, Grow.Std) {
            modifier.padding(Dimensions.PaddingMedium)

            Box(Grow(0.65f), Grow.Std) {
                modifier.margin(end = Dimensions.PaddingMedium)
                    .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingSmall))

                ScrollArea(Grow.Std, Grow.Std, containerModifier = { it.backgroundColor(null) }, withHorizontalScrollbar = false) {
                    modifier.padding(Dimensions.PaddingMedium)
                        .layout(ColumnLayout)
                    EditorForm()
                }
            }

            Box(Grow(0.35f), Grow.Std) {
                modifier.background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingSmall))
                    .padding(Dimensions.PaddingMedium)

                Preview()
            }
        }
    }

    private fun UiScope.EditorForm() {
        SectionHeader("Item")
        LabeledText("Id", id, "optional, use modid:path")
        LabeledText("Max Stack", maxStack, "1..64")
        LabeledText("Max Damage", maxDamage, "durability")
        LabeledText("Rarity", rarity, "common/uncommon/rare/epic")
        LabeledCheckbox("Fire Resistant", fireResistant)
        LabeledTabSelector()

        SectionHeader("Model")
        LabeledTemplate("Template", templateIndex, templateOptions)
        LabeledText("Texture", modelTexture, "namespace:item/texture")

        SectionHeader("Advanced Model")
        LabeledText("Model Id", model, "custom parent id")
        LabeledText("Model Parent", modelParent, "item/generated or custom")
        LabeledText("Model JSON", modelJson, "raw json overrides other model fields")
    }

    private fun UiScope.Preview() {
        val resolvedId = resolvePreviewId()
        val item = resolvedId?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }

        Column(Grow.Std, Grow.Std) {
            Text("Preview") {
                modifier.textColor(Color.WHITE).margin(bottom = Dimensions.PaddingSmall)
            }

            if (item == null) {
                Text("No item registered for id") {
                    modifier.textColor(ColorTheme.UI.WhiteReplacement)
                }
                resolvedId?.let { idValue ->
                    Text(idValue.toString()) {
                        modifier.textColor(ColorTheme.UI.BackgroundAccent)
                    }
                }
            } else {
                Item(item.defaultInstance) {
                    modifier.size(96.dp, 96.dp)
                        .margin(bottom = Dimensions.PaddingSmall)
                }
                Text(resolvedId.toString()) {
                    modifier.textColor(ColorTheme.UI.WhiteReplacement)
                }
            }
        }
    }

    private fun UiScope.SectionHeader(text: String) {
        Text(text) {
            modifier.textColor(ColorTheme.UI.WhiteReplacement)
                .margin(bottom = Dimensions.PaddingSmall, top = Dimensions.PaddingMedium)
                .font(sizes.normalText.derive(30f))
        }
    }

    private fun UiScope.LabeledText(label: String, state: MutableStateValue<String>, hint: String) {
        Column(Grow.Std) {
            Text(label) {
                modifier.textColor(ColorTheme.UI.WhiteReplacement)
                    .margin(bottom = Dimensions.PaddingSmall)
            }
            TextField(state.use()) {
                modifier.width(Grow.Std)
                    .backgroundColor(ColorTheme.UI.BackgroundElements)
                    .padding(Dimensions.PaddingNormal)
                    .colors(lineColor = Color.WHITE.withAlpha(0f), lineColorFocused = Color.WHITE.withAlpha(0f))
                    .hint(hint)
                    .onChange { state.set(it) }
                modifier.textColor = ColorTheme.UI.WhiteReplacement
            }
            modifier.margin(bottom = Dimensions.PaddingMedium)
        }
    }

    private fun UiScope.LabeledTemplate(label: String, index: MutableStateValue<Int>, options: List<String>) {
        Column(Grow.Std) {
            Text(label) {
                modifier.textColor(ColorTheme.UI.WhiteReplacement)
                    .margin(bottom = Dimensions.PaddingSmall)
            }
            ComboBox("Select template", options.map { option ->
                Composable {
                    Text(option) { modifier.textColor(ColorTheme.UI.WhiteReplacement) }
                }
            }, index)
            modifier.margin(bottom = Dimensions.PaddingMedium)
        }
    }

    private fun UiScope.LabeledTabSelector() {
        val tabs = BuiltInRegistries.CREATIVE_MODE_TAB.keySet()
            .map { it.toString() }
            .sorted()
        cachedTabOptions = tabs
        val options = tabs + listOf("Custom")
        if (tabIndex.use() == -1 && tab.value.isNotBlank()) {
            setTabFromValue(tab.value)
        }

        Column(Grow.Std) {
            Text("Creative Tab") {
                modifier.textColor(ColorTheme.UI.WhiteReplacement)
                    .margin(bottom = Dimensions.PaddingSmall)
            }
            ComboBox("Select tab", options.map { option ->
                Composable {
                    Text(option) { modifier.textColor(ColorTheme.UI.WhiteReplacement) }
                }
            }, tabIndex)
            modifier.margin(bottom = Dimensions.PaddingSmall)

            if (tabIndex.use() == options.lastIndex) {
                TextField(customTab.use()) {
                    modifier.width(Grow.Std)
                        .backgroundColor(ColorTheme.UI.BackgroundElements)
                        .padding(Dimensions.PaddingNormal)
                        .colors(lineColor = Color.WHITE.withAlpha(0f), lineColorFocused = Color.WHITE.withAlpha(0f))
                        .hint("namespace:tab")
                        .onChange {
                            customTab.set(it)
                            tab.set(it.trim())
                        }
                    modifier.textColor = ColorTheme.UI.WhiteReplacement
                }
                modifier.margin(bottom = Dimensions.PaddingMedium)
            } else {
                modifier.margin(bottom = Dimensions.PaddingMedium)
            }
        }
    }

    private fun UiScope.LabeledCheckbox(label: String, state: MutableStateValue<Boolean>) {
        Row(Grow.Std) {
            Checkbox(state.use()) {
                modifier.onToggle { state.set(it) }
                    .margin(end = Dimensions.PaddingSmall)
            }
            Text(label) {
                modifier.textColor(ColorTheme.UI.WhiteReplacement)
                    .alignY(AlignmentY.Center)
            }
        }
        Box(Grow.Std) { modifier.height(Dimensions.PaddingMedium) }
    }

    private fun applyPrefab(prefab: ItemPrefab) {
        id.set(prefab.id.orEmpty())
        maxStack.set(prefab.maxStack?.toString().orEmpty())
        maxDamage.set(prefab.maxDamage?.toString().orEmpty())
        rarity.set(prefab.rarity.orEmpty())
        fireResistant.set(prefab.fireResistant)
        tab.set(prefab.tab.orEmpty())
        customTab.set(prefab.tab.orEmpty())

        model.set(prefab.model.orEmpty())
        modelParent.set(prefab.modelParent.orEmpty())
        modelTexture.set(prefab.modelTexture.orEmpty())
        modelJson.set(prefab.modelJson.orEmpty())

        setTemplateFromModel(prefab.model)
        setTabFromValue(prefab.tab)
    }

    private fun buildPrefab(): ItemPrefab {
        return ItemPrefab(
            id = id.value.trim().ifBlank { null },
            maxStack = parseInt(maxStack.value),
            maxDamage = parseInt(maxDamage.value),
            rarity = rarity.value.trim().ifBlank { null },
            fireResistant = fireResistant.value,
            tab = tab.value.trim().ifBlank { null },
            model = model.value.trim().ifBlank { null },
            modelParent = modelParent.value.trim().ifBlank { null },
            modelTexture = modelTexture.value.trim().ifBlank { null },
            modelJson = modelJson.value.trim().ifBlank { null },
        )
    }

    private fun parseInt(value: String): Int? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.toIntOrNull()
    }

    private fun resolvePreviewId(): ResourceLocation? {
        val explicit = id.value.trim().ifBlank { null }
        val raw = explicit ?: defaultIdFromPath() ?: return null
        val full = if (raw.contains(':')) raw else "${HollowEngine.MODID}:$raw"
        return ResourceLocation.tryParse(full)
    }

    private fun defaultIdFromPath(): String? {
        val normalized = filePath.replace("\\", "/")
        val relative = when {
            normalized.startsWith("prefabs/items/") -> normalized.removePrefix("prefabs/items/")
            normalized.startsWith("prefabs/") -> normalized.removePrefix("prefabs/")
            else -> return null
        }
        val base = relative
            .removeSuffix(".item.prefab")
            .removeSuffix(".item.json")
            .removeSuffix(".item.yml")
            .removeSuffix(".item.yaml")
        if (base.isBlank()) return null
        return "${HollowEngine.MODID}:$base"
    }

    private fun setTemplateFromModel(value: String?) {
        val idx = templateOptions.indexOf(value?.lowercase())
        if (idx >= 0) {
            templateIndex.set(idx)
        } else {
            templateIndex.set(-1)
        }
    }

    init {
        templateIndex.onChange { _, newIndex ->
            if (newIndex in templateOptions.indices) {
                model.set(templateOptions[newIndex])
            }
        }
        tabIndex.onChange { _, newIndex ->
            val options = cachedTabOptions
            if (newIndex in options.indices) {
                val value = options[newIndex]
                tab.set(value)
                customTab.set(value)
            }
        }
    }

    private fun setTabFromValue(value: String?) {
        val valStr = value?.trim().orEmpty()
        if (valStr.isEmpty()) {
            tabIndex.set(-1)
            return
        }
        if (cachedTabOptions.isEmpty()) {
            tabIndex.set(-1)
            return
        }
        val idx = cachedTabOptions.indexOf(valStr)
        if (idx >= 0) {
            tabIndex.set(idx)
        } else {
            tabIndex.set(cachedTabOptions.size) // Custom
        }
    }
}
