package ru.hollowhorizon.hollowengine.client.gui.scripting.files.tags

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.IDEFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.common.tags.TagManager
import ru.hollowhorizon.hollowengine.common.utils.rl

class TagEditorFile(path: String) : IDEFile(path) {
    private val searchQuery = mutableStateOf("")
    private val selectedRegistryType = mutableStateOf(RegistryType.ALL)
    private val selectedTag = mutableStateOf<TagData?>(null)
    private val deletedTags = mutableStateListOf<TagData>()
    private val showDeletedTags = mutableStateOf(false)
    private val newEntryInput = mutableStateOf("")
    private val hiddenCategories = mutableStateListOf<String>()

    enum class RegistryType {
        ALL, BLOCK, ITEM
    }

    data class TagData(
        val key: ResourceLocation,
        val type: RegistryType,
        val entries: MutableStateList<ResourceLocation>,
        val isDeleted: Boolean = false,
    )

    override fun save() {
        // Tags are saved automatically through TagManager
    }

    override fun UiScope.compose() {
        modifier.backgroundColor(ColorTheme.UI.BackgroundGeneral)

        Column(Grow.Std, Grow.Std) {
            modifier.padding(Dimensions.PaddingMedium)

            Header()

            Row(Grow.Std, Grow.Std) {
                modifier.margin(top = Dimensions.PaddingMedium)

                TagsList()
                TagDetails()
            }
        }
    }

    private fun UiScope.Header() {
        Row(Grow.Std) {
            modifier.padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingSmall))

            Box(Grow.Std, Grow.Std) {
                modifier.margin(end = Dimensions.PaddingMedium)
                TextField(searchQuery.use()) {
                    modifier.size(Grow.Std, Grow.Std).backgroundColor(ColorTheme.UI.BackgroundElements)
                        .onChange { searchQuery.set(it) }
                        .colors(lineColor = Color.WHITE.withAlpha(0f), lineColorFocused = Color.WHITE.withAlpha(0f))
                        .padding(Dimensions.PaddingNormal)
                    modifier.textColor = ColorTheme.UI.WhiteReplacement
                    modifier.hint("Search tags...")
                }
            }

            Box {
                modifier.margin(end = Dimensions.PaddingMedium)

                val isHovered by modifier.hoverable()
                val bgColor by animateColorAsState(
                    if (isHovered) ColorTheme.UI.BackgroundAccent else ColorTheme.UI.BackgroundElements,
                    tween(easing = Easing.easeOutQuart)
                )

                Button("Фильтр: ${selectedRegistryType.use().name}") {
                    modifier.backgroundColor(bgColor).textColor(ColorTheme.UI.WhiteReplacement)
                        .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingNormal).onClick {
                            val types = RegistryType.entries.toTypedArray()
                            val currentIndex = types.indexOf(selectedRegistryType.value)
                            selectedRegistryType.set(types[(currentIndex + 1) % types.size])
                        }
                }
            }

            Box {
                modifier.margin(end = Dimensions.PaddingMedium)

                val isHovered by modifier.hoverable()
                val bgColor by animateColorAsState(
                    if (isHovered) ColorTheme.Accents.Success else ColorTheme.UI.BackgroundElements,
                    tween(easing = Easing.easeOutQuart)
                )

                Button("+ Новый тег") {
                    modifier.backgroundColor(bgColor).textColor(ColorTheme.UI.WhiteReplacement)
                        .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingNormal).onClick {
                            createNewTag()
                        }
                }
            }

            Box {
                val isHovered by modifier.hoverable()
                val bgColor by animateColorAsState(
                    if (showDeletedTags.use()) ColorTheme.Console.Warning
                    else if (isHovered) ColorTheme.UI.BackgroundAccent
                    else ColorTheme.UI.BackgroundElements, tween(easing = Easing.easeOutQuart)
                )

                Button("Удалённые (${deletedTags.size})") {
                    modifier.backgroundColor(bgColor).textColor(ColorTheme.UI.WhiteReplacement)
                        .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingNormal).onClick {
                            showDeletedTags.set(!showDeletedTags.value)
                        }
                }
            }
        }
    }

    private fun UiScope.TagsList() {
        Box(Grow(0.4f), Grow.Std) {
            modifier.margin(end = Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingSmall))

            Column(Grow.Std, Grow.Std) {
                modifier.padding(Dimensions.PaddingMedium)

                Text("Теги") {
                    modifier.font(sizes.largeText).textColor(ColorTheme.UI.WhiteReplacement)
                        .margin(bottom = Dimensions.PaddingMedium)
                }

                LazyColumn(Grow.Std, Grow.Std, containerModifier = {
                    it.background(null)
                }, hScrollbarModifier = {
                    it.colors(
                        trackColor = ColorTheme.UI.BackgroundSecondary,
                        trackHoverColor = ColorTheme.UI.BackgroundElements,
                        color = ColorTheme.UI.BackgroundAccent,
                        hoverColor = ColorTheme.UI.WhiteReplacement
                    ).height(Dimensions.PaddingMedium).margin(Dimensions.PaddingMedium)
                        .margin(end = Dimensions.PaddingHuge)
                }, vScrollbarModifier = {
                    it.colors(
                        trackColor = ColorTheme.UI.BackgroundSecondary,
                        trackHoverColor = ColorTheme.UI.BackgroundElements,
                        color = ColorTheme.UI.BackgroundAccent,
                        hoverColor = ColorTheme.UI.WhiteReplacement
                    ).width(Dimensions.PaddingMedium).margin(Dimensions.PaddingMedium)
                        .margin(bottom = Dimensions.PaddingHuge)
                }) {
                    val tags = if (showDeletedTags.use()) {
                        deletedTags.toList()
                    } else {
                        getAllTags().filter { !it.isDeleted }
                    }

                    val filteredTags = tags.filter { tag ->
                        val matchesSearch = searchQuery.use().isEmpty() || tag.key.toString()
                            .contains(searchQuery.use(), ignoreCase = true)
                        val matchesType =
                            selectedRegistryType.use() == RegistryType.ALL || tag.type == selectedRegistryType.use()
                        matchesSearch && matchesType
                    }.sortedBy { it.key }

                    val groupedTags = filteredTags.groupBy { it.key.namespace }

                    val items = mutableListOf<Composable>()
                    groupedTags.forEach { (namespace, tagsInNamespace) ->
                        val isGroupHidden = hiddenCategories.contains(namespace)

                        items += Composable {
                            Row {
                                val isHovered by modifier.hoverable()

                                val bgColor by animateColorAsState(
                                    when {
                                        isHovered -> ColorTheme.UI.BackgroundAccent
                                        else -> Color.WHITE.withAlpha(0f)
                                    }, tween(easing = Easing.easeOutQuart)
                                )

                                modifier.padding(Dimensions.PaddingNormal).margin(vertical = Dimensions.PaddingSmall)
                                    .background(RoundRectBackground(bgColor, Dimensions.PaddingSmall))
                                    .onClick {
                                        if (hiddenCategories.contains(namespace)) {
                                            hiddenCategories.remove(namespace)
                                        } else {
                                            hiddenCategories.add(namespace)
                                        }
                                    }

                                Arrow(if (isGroupHidden) ArrowScope.ROTATION_LEFT else ArrowScope.ROTATION_DOWN) {
                                    modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                                        .margin(end = Dimensions.PaddingMedium)
                                }
                                Text("$namespace") {
                                    modifier.font(sizes.normalText).textColor(ColorTheme.Accents.Main)
                                        .margin(vertical = Dimensions.PaddingNormal)
                                }
                            }
                        }

                        if (!isGroupHidden) {
                            tagsInNamespace.forEach { tag ->
                                items += Composable {
                                    TagListItem(tag)
                                }
                            }
                        }
                    }

                    items(items) { item ->
                        item()
                    }
                }
            }
        }
    }

    private fun UiScope.TagListItem(tag: TagData) {
        Box(Grow.Std) {
            val isSelected = selectedTag.use()?.let { it.key == tag.key && it.type == tag.type } == true
            val isHovered by modifier.hoverable()

            val bgColor by animateColorAsState(
                when {
                    isSelected -> ColorTheme.Accents.Main.mix(ColorTheme.UI.BackgroundAccent, 0.5f)
                    isHovered -> ColorTheme.UI.BackgroundAccent
                    tag.isDeleted -> ColorTheme.Console.Error.withAlpha(0.3f)
                    else -> Color.WHITE.withAlpha(0f)
                }, tween(easing = Easing.easeOutQuart)
            )

            modifier.padding(Dimensions.PaddingNormal).margin(vertical = Dimensions.PaddingSmall)
                .background(RoundRectBackground(bgColor, Dimensions.PaddingSmall)).onClick {
                    selectedTag.set(tag)
                }

            Row {
                modifier.align(AlignmentX.Start, AlignmentY.Center)

                Box {
                    modifier.size(Dimensions.PaddingMedium, Dimensions.PaddingMedium)
                        .margin(end = Dimensions.PaddingMedium).background(
                            RoundRectBackground(
                                when (tag.type) {
                                    RegistryType.BLOCK -> ColorTheme.Icons.Data
                                    RegistryType.ITEM -> ColorTheme.Icons.Assets
                                    else -> ColorTheme.UI.WhiteReplacement
                                }, Dimensions.PaddingSmall
                            )
                        )
                        .alignY(AlignmentY.Center)
                }

                Column {
                    Text(tag.key.path) {
                        modifier.font(sizes.normalText).textColor(ColorTheme.UI.WhiteReplacement)
                    }
                    Text("${tag.entries.size} записей") {
                        modifier.font(sizes.smallText).textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.7f))
                    }
                }
            }
        }
    }

    private fun UiScope.TagDetails() {
        Box(Grow(0.6f), Grow.Std) {
            modifier.background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingSmall))

            val tag = selectedTag.use()
            if (tag == null) {
                Box(Grow.Std, Grow.Std) {
                    Text("Выберите тег для просмотра справа") {
                        modifier.align(AlignmentX.Center, AlignmentY.Center)
                            .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.5f))
                            .width(Grow.Std).isWrapText(true)
                    }
                }
            } else {
                Column(Grow.Std, Grow.Std) {
                    modifier.padding(Dimensions.PaddingMedium)

                    Row(Grow.Std) {
                        Column(Grow.Std) {
                            Text(tag.key.toString()) {
                                modifier.font(sizes.largeText).textColor(ColorTheme.UI.WhiteReplacement)
                            }
                            Text("Тип: ${tag.type.name}") {
                                modifier.font(sizes.normalText)
                                    .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.7f))
                                    .margin(top = Dimensions.PaddingSmall)
                            }
                            Text("Записей: ${tag.entries.size}") {
                                modifier.font(sizes.normalText)
                                    .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.7f))
                            }
                        }

                        Box {
                            val isHovered by modifier.hoverable()
                            val bgColor by animateColorAsState(
                                if (isHovered) {
                                    if (tag.isDeleted) ColorTheme.Accents.Success else ColorTheme.Console.Error
                                } else ColorTheme.UI.BackgroundElements, tween(easing = Easing.easeOutQuart)
                            )

                            Button(if (tag.isDeleted) "Восстановить" else "Удалить") {
                                modifier.backgroundColor(bgColor).textColor(ColorTheme.UI.WhiteReplacement)
                                    .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingNormal)
                                    .onClick {
                                        if (tag.isDeleted) {
                                            restoreTag(tag)
                                        } else {
                                            deleteTag(tag)
                                        }
                                    }
                            }
                        }
                    }

                    if (!tag.isDeleted) {
                        Row(Grow.Std) {
                            modifier.margin(top = Dimensions.PaddingMedium, bottom = Dimensions.PaddingMedium)

                            Box(Grow.Std, Grow.Std) {
                                modifier.margin(end = Dimensions.PaddingMedium)

                                TextField(newEntryInput.use()) {
                                    modifier.size(Grow.Std, Grow.Std).backgroundColor(ColorTheme.UI.BackgroundElements)
                                        .onChange { newEntryInput.set(it) }
                                        .colors(lineColor = Color.WHITE.withAlpha(0f), lineColorFocused = Color.WHITE.withAlpha(0f))
                                        .padding(Dimensions.PaddingNormal)
                                    modifier.textColor = ColorTheme.UI.WhiteReplacement
                                    modifier.hint("minecraft:stone")
                                }
                            }

                            Box {
                                val isHovered by modifier.hoverable()
                                val bgColor by animateColorAsState(
                                    if (isHovered) ColorTheme.Accents.Success else ColorTheme.UI.BackgroundElements,
                                    tween(easing = Easing.easeOutQuart)
                                )

                                Button("+ Добавить") {
                                    modifier.backgroundColor(bgColor).textColor(ColorTheme.UI.WhiteReplacement).padding(
                                        horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingNormal
                                    ).onClick {

                                        addEntryToTag(tag, newEntryInput.value)
                                        newEntryInput.set("")
                                    }
                                }
                            }
                        }
                    }

                    Text("Содержимое:") {
                        modifier.font(sizes.normalText).textColor(ColorTheme.UI.WhiteReplacement)
                            .margin(bottom = Dimensions.PaddingNormal)
                    }

                    LazyColumn(Grow.Std, Grow.Std, containerModifier = {
                        it.background(null)
                    }, hScrollbarModifier = {
                        it.colors(
                            trackColor = ColorTheme.UI.BackgroundSecondary,
                            trackHoverColor = ColorTheme.UI.BackgroundElements,
                            color = ColorTheme.UI.BackgroundAccent,
                            hoverColor = ColorTheme.UI.WhiteReplacement
                        ).height(Dimensions.PaddingMedium)
                    }, vScrollbarModifier = {
                        it.colors(
                            trackColor = ColorTheme.UI.BackgroundSecondary,
                            trackHoverColor = ColorTheme.UI.BackgroundElements,
                            color = ColorTheme.UI.BackgroundAccent,
                            hoverColor = ColorTheme.UI.WhiteReplacement
                        ).width(Dimensions.PaddingMedium)
                    }) {
                        modifier.margin(end = Dimensions.PaddingHuge, bottom = Dimensions.PaddingMedium)
                        items(tag.entries.toList()) { entry ->
                            EntryItem(tag, entry)
                        }
                    }
                }
            }
        }
    }

    private fun UiScope.EntryItem(tag: TagData, entry: ResourceLocation) {
        Box(Grow.Std) {
            val isHovered by modifier.hoverable()
            val bgColor by animateColorAsState(
                if (isHovered) ColorTheme.UI.BackgroundAccent else ColorTheme.UI.BackgroundElements,
                tween(easing = Easing.easeOutQuart)
            )

            modifier.padding(Dimensions.PaddingNormal).margin(vertical = Dimensions.PaddingSmall)
                .background(RoundRectBackground(bgColor, Dimensions.PaddingSmall))

            Row(Grow.Std) {
                modifier.align(AlignmentX.Start, AlignmentY.Center)

                val item = BuiltInRegistries.ITEM.getOptional(entry).orElse(null)
                if (item != null) {
                    Item(item.defaultInstance) {
                        modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                            .margin(end = Dimensions.PaddingMedium).border(null)
                            .alignY(AlignmentY.Center)
                    }
                }

                Text(item?.defaultInstance?.hoverName?.string ?: entry.toString()) {
                    modifier.font(sizes.normalText).textColor(ColorTheme.UI.WhiteReplacement).width(Grow.Std)
                        .alignY(AlignmentY.Center)
                }

                if (!tag.isDeleted) {
                    Box {
                        val removeHovered by modifier.hoverable()
                        val removeBgColor by animateColorAsState(
                            if (removeHovered) ColorTheme.Console.Error else Color.WHITE.withAlpha(0f),
                            tween(easing = Easing.easeOutQuart)
                        )

                        Button("×") {
                            modifier.backgroundColor(removeBgColor).textColor(ColorTheme.UI.WhiteReplacement)
                                .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingSmall)
                                .onClick {
                                    removeEntryFromTag(tag, entry)
                                }
                        }
                    }
                }
            }
        }
    }

    private fun getAllTags(): List<TagData> {
        val tags = mutableListOf<TagData>()

        TagManager.BLOCK_TAGS.forEach { (tagKey, blocks) ->
            tags.add(
                TagData(
                    key = tagKey.location,
                    type = RegistryType.BLOCK,
                    entries = MutableStateList<ResourceLocation>().apply {
                        addAll(blocks.map { BuiltInRegistries.BLOCK.getKey(it) })
                    })
            )
        }

        TagManager.ITEM_TAGS.forEach { (tagKey, items) ->
            tags.add(
                TagData(
                    key = tagKey.location,
                    type = RegistryType.ITEM,
                    entries = MutableStateList<ResourceLocation>().apply {
                        addAll(items.map { BuiltInRegistries.ITEM.getKey(it) })
                    })
            )
        }

        val connection = Minecraft.getInstance().connection
        if (connection != null) {
            connection.registryAccess().registry(Registries.BLOCK).ifPresent { registry ->
                registry.tags.forEach { tagPair ->
                    val tagKey = tagPair.first
                    val holders = tagPair.second
                    if (tags.none { it.key == tagKey.location && it.type == RegistryType.BLOCK }) {
                        tags.add(
                            TagData(
                                key = tagKey.location,
                                type = RegistryType.BLOCK,
                                entries = MutableStateList<ResourceLocation>().apply {
                                    holders.forEach { holder ->
                                        holder.unwrapKey().ifPresent { key ->
                                            add(key.location())
                                        }
                                    }
                                })
                        )
                    }
                }
            }

            connection.registryAccess().registry(Registries.ITEM).ifPresent { registry ->
                registry.tags.forEach { tagPair ->
                    val tagKey = tagPair.first
                    val holders = tagPair.second
                    if (tags.none { it.key == tagKey.location && it.type == RegistryType.ITEM }) {
                        tags.add(
                            TagData(
                                key = tagKey.location,
                                type = RegistryType.ITEM,
                                entries = MutableStateList<ResourceLocation>().apply {
                                    holders.forEach { holder ->
                                        holder.unwrapKey().ifPresent { key ->
                                            add(key.location())
                                        }
                                    }
                                })
                        )
                    }
                }
            }
        }

        return tags
    }

    private fun createNewTag() {
        val newTag = TagData(
            key = "custom:new_tag_${System.currentTimeMillis()}".rl,
            type = RegistryType.ITEM,
            entries = MutableStateList()
        )
        selectedTag.set(newTag)
    }

    private fun deleteTag(tag: TagData) {
        val deletedTag = tag.copy(isDeleted = true)
        deletedTags.add(deletedTag)
        selectedTag.set(null)

        when (tag.type) {
            RegistryType.BLOCK -> {
                val tagKey = TagKey.create(Registries.BLOCK, tag.key)
                TagManager.BLOCK_TAGS.remove(tagKey)
            }

            RegistryType.ITEM -> {
                val tagKey = TagKey.create(Registries.ITEM, tag.key)
                TagManager.ITEM_TAGS.remove(tagKey)
            }

            else -> {}
        }
    }

    private fun restoreTag(tag: TagData) {
        deletedTags.remove(tag)

        // Add back to TagManager
        when (tag.type) {
            RegistryType.BLOCK -> {
                val tagKey = TagKey.create(Registries.BLOCK, tag.key)
                val blocks = tag.entries.mapNotNull { BuiltInRegistries.BLOCK.get(it) }.toMutableSet()
                TagManager.BLOCK_TAGS[tagKey] = blocks
            }

            RegistryType.ITEM -> {
                val tagKey = TagKey.create(Registries.ITEM, tag.key)
                val items = tag.entries.mapNotNull { BuiltInRegistries.ITEM.get(it) }.toMutableSet()
                TagManager.ITEM_TAGS[tagKey] = items
            }

            else -> {}
        }

        selectedTag.set(null)
    }

    private fun addEntryToTag(tag: TagData, entryString: String) {
        if (entryString.isBlank()) return

        try {
            val entryLocation = entryString.rl
            if (!tag.entries.contains(entryLocation)) {
                tag.entries.add(entryLocation)

                when (tag.type) {
                    RegistryType.BLOCK -> {
                        val tagKey = TagKey.create(Registries.BLOCK, tag.key)
                        val block = BuiltInRegistries.BLOCK.getOptional(entryLocation).orElse(null)
                        if (block != null) {
                            TagManager.BLOCK_TAGS.getOrPut(tagKey) { HashSet() }.add(block)
                        }
                    }

                    RegistryType.ITEM -> {
                        val tagKey = TagKey.create(Registries.ITEM, tag.key)
                        val item = BuiltInRegistries.ITEM.getOptional(entryLocation).orElse(null)
                        if (item != null) {
                            TagManager.ITEM_TAGS.getOrPut(tagKey) { HashSet() }.add(item)
                        }
                    }

                    else -> {}
                }
            }
        } catch (e: Exception) {
            // Invalid resource location format
        }
    }

    private fun removeEntryFromTag(tag: TagData, entry: ResourceLocation) {
        tag.entries.remove(entry)

        when (tag.type) {
            RegistryType.BLOCK -> {
                val tagKey = TagKey.create(Registries.BLOCK, tag.key)
                val block = BuiltInRegistries.BLOCK.get(entry)
                TagManager.BLOCK_TAGS[tagKey]?.remove(block)
            }

            RegistryType.ITEM -> {
                val tagKey = TagKey.create(Registries.ITEM, tag.key)
                val item = BuiltInRegistries.ITEM.get(entry)
                TagManager.ITEM_TAGS[tagKey]?.remove(item)
            }

            else -> {}
        }
    }
}
