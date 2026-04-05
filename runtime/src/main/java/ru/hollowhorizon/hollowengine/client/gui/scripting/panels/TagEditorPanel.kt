package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.common.network.CUpdateTagPacket
import ru.hollowhorizon.hollowengine.common.utils.rl

class TagEditorPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.tags", dock) {
    override val icon = icons.RECIPES
    private val searchQuery = mutableStateOf("")
    private val selectedRegistryType = mutableStateOf(RegistryType.ALL)
    private val selectedTag = mutableStateOf<TagData?>(null)
    private val deletedTags = mutableStateListOf<TagData>()
    private val showDeletedTags = mutableStateOf(false)
    private val newEntryInput = mutableStateOf("")
    private val tagStats = mutableStateOf(TagStats(0, 0, 0))
    private val hiddenCategories = mutableStateListOf<String>()

    enum class RegistryType {
        ALL, BLOCK, ITEM;

        fun toLang(): String = when (this) {
            ALL -> "hollowengine.tags.filter_types.all".lang
            BLOCK -> "hollowengine.tags.filter_types.block".lang
            ITEM -> "hollowengine.tags.filter_types.item".lang
        }
    }

    data class TagData(
        val key: ResourceLocation,
        val type: RegistryType,
        val entries: MutableStateList<ResourceLocation>,
        var isDeleted: Boolean = false,
    )

    data class TagStats(
        val totalTags: Int,
        val blockTags: Int,
        val itemTags: Int,
    )

    override fun UiScope.compose() {
        modifier.backgroundColor(ColorTheme.UI.BackgroundGeneral)
        updateTagStats()

        Column(Grow.Std, Grow.Std) {
            modifier.padding(Dimensions.PaddingMedium)

            Header()

            Row(Grow.Std, Grow.Std) {
                modifier.margin(top = Dimensions.PaddingMedium)

                Column(Grow(0.4f), Grow.Std) {
                    modifier.margin(end = Dimensions.PaddingMedium)

                    StatisticsPanel()

                    Box(Grow.Std, Grow.Std) {
                        modifier.margin(top = Dimensions.PaddingMedium)
                            .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingSmall))
                        TagsList()
                    }
                }

                Box(Grow(0.6f), Grow.Std) {
                    modifier.background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingSmall))
                    TagDetails()
                }
            }
        }
    }

    private fun UiScope.Header() {
        Row(Grow.Std) {
            modifier.padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingSmall))

            Box(Grow(1f)) {
                modifier.margin(end = Dimensions.PaddingMedium)

                TextField(searchQuery.use()) {
                    modifier.size(Grow.Std, Grow.Std).backgroundColor(ColorTheme.UI.BackgroundElements)
                        .onChange { newEntryInput.set(it) }
                        .colors(lineColor = Color.WHITE.withAlpha(0f), lineColorFocused = Color.WHITE.withAlpha(0f))
                        .padding(Dimensions.PaddingNormal)
                    modifier.textColor = ColorTheme.UI.WhiteReplacement
                    modifier.hint("hollowengine.tags.search_hint".lang)
                        .onChange { searchQuery.set(it) }
                }
            }

            FilterButton()
            NewTagButton()
            DeletedToggle()
        }
    }

    private fun UiScope.FilterButton() {
        Box {
            modifier.margin(end = Dimensions.PaddingMedium)
            val isHovered by modifier.hoverable()
            val bgColor by animateColorAsState(
                if (isHovered) ColorTheme.UI.BackgroundAccent else ColorTheme.UI.BackgroundElements,
                tween(easing = Easing.easeOutQuart)
            )

            Button("${"hollowengine.tags.filter".lang}: ${selectedRegistryType.use().toLang()}") {
                modifier.backgroundColor(bgColor)
                    .textColor(ColorTheme.UI.WhiteReplacement)
                    .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingNormal)
                    .onClick {
                        val types = RegistryType.values()
                        val currentIndex = types.indexOf(selectedRegistryType.value)
                        selectedRegistryType.set(types[(currentIndex + 1) % types.size])
                    }
            }
        }
    }

    private fun UiScope.NewTagButton() {
        Box {
            modifier.margin(end = Dimensions.PaddingMedium)
            val isHovered by modifier.hoverable()
            val bgColor by animateColorAsState(
                if (isHovered) ColorTheme.Accents.Success else ColorTheme.UI.BackgroundElements,
                tween(easing = Easing.easeOutQuart)
            )

            Button("+ ${"hollowengine.tags.new_tag".lang}") {
                modifier.backgroundColor(bgColor)
                    .textColor(ColorTheme.UI.WhiteReplacement)
                    .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingNormal)
                    .onClick { createNewTag() }
            }
        }
    }

    private fun UiScope.DeletedToggle() {
        Box {
            val isHovered by modifier.hoverable()
            val bgColor by animateColorAsState(
                if (showDeletedTags.use()) ColorTheme.Console.Warning
                else if (isHovered) ColorTheme.UI.BackgroundAccent
                else ColorTheme.UI.BackgroundElements,
                tween(easing = Easing.easeOutQuart)
            )

            Button("${"hollowengine.tags.deleted".lang} (${deletedTags.size})") {
                modifier.backgroundColor(bgColor)
                    .textColor(ColorTheme.UI.WhiteReplacement)
                    .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingNormal)
                    .onClick { showDeletedTags.set(!showDeletedTags.value) }
            }
        }
    }

    private fun UiScope.StatisticsPanel() {
        Box(Grow.Std) {
            modifier.padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingSmall))

            val stats = tagStats.use()
            Row(Grow.Std) {
                StatItem("hollowengine.tags.stats.total".lang, stats.totalTags, ColorTheme.Accents.Main)
                StatItem("hollowengine.tags.stats.blocks".lang, stats.blockTags, ColorTheme.Icons.Data)
                StatItem("hollowengine.tags.stats.items".lang, stats.itemTags, ColorTheme.Icons.Assets)
            }
        }
    }

    private fun UiScope.StatItem(label: String, value: Int, color: Color) {
        Column(Grow.Std) {
            modifier.alignY(AlignmentY.Center)

            Text(label) {
                modifier.font(sizes.smallText)
                    .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.7f))
                    .alignX(AlignmentX.Center)
            }

            Text(value.toString()) {
                modifier.font(sizes.largeText)
                    .textColor(color)
                    .alignX(AlignmentX.Center)
            }
        }
    }

    private fun UiScope.TagsList() {
        Column(Grow.Std, Grow.Std) {
            modifier.padding(Dimensions.PaddingMedium)

            Text("hollowengine.tags.list_title".lang) {
                modifier.font(sizes.largeText)
                    .textColor(ColorTheme.UI.WhiteReplacement)
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
                    val isGroupHidden = hiddenCategories.use().contains(namespace)

                    items += Composable {
                        Row(Grow.Std) {
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


    private fun UiScope.TagListItem(tag: TagData) {
        Box(Grow.Std) {
            val isSelected = selectedTag.use() == tag
            val isHovered by modifier.hoverable()
            val bgColor by animateColorAsState(
                when {
                    isSelected -> ColorTheme.Accents.Main
                    isHovered -> ColorTheme.UI.BackgroundAccent
                    tag.isDeleted -> ColorTheme.Console.Error.withAlpha(0.3f)
                    else -> Color.WHITE.withAlpha(0f)
                },
                tween(easing = Easing.easeOutQuart)
            )

            modifier.padding(Dimensions.PaddingNormal)
                .margin(vertical = Dimensions.PaddingSmall)
                .background(RoundRectBackground(bgColor, Dimensions.PaddingSmall))
                .onClick { selectedTag.set(tag) }

            Row {
                modifier.align(AlignmentX.Start, AlignmentY.Center)
                TypeIndicator(tag.type)
                Column {
                    Text(tag.key.path) {
                        modifier.font(sizes.normalText).textColor(ColorTheme.UI.WhiteReplacement)
                    }
                    Text("${tag.entries.size} ${"hollowengine.tags.entries_count".lang}") {
                        modifier.font(sizes.smallText).textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.7f))
                    }
                }
            }
        }
    }

    private fun UiScope.TypeIndicator(type: RegistryType) {
        Box {
            modifier.size(Dimensions.PaddingMedium, Dimensions.PaddingMedium)
                .margin(end = Dimensions.PaddingNormal)
                .background(
                    RoundRectBackground(
                        when (type) {
                            RegistryType.BLOCK -> ColorTheme.Icons.Data
                            RegistryType.ITEM -> ColorTheme.Icons.Assets
                            else -> ColorTheme.UI.WhiteReplacement
                        },
                        Dimensions.PaddingSmall
                    )
                )
                .alignY(AlignmentY.Center)
        }
    }

    private fun UiScope.TagDetails() {
        val tag = selectedTag.use()
        if (tag == null) {
            Box(Grow.Std, Grow.Std) {
                Text("hollowengine.tags.select_hint".lang) {
                    modifier.align(AlignmentX.Center, AlignmentY.Center)
                        .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.5f))
                }
            }
            return
        }

        Column(Grow.Std, Grow.Std) {
            modifier.padding(Dimensions.PaddingMedium)
            TagDetailsHeader(tag)
            if (!tag.isDeleted) AddEntrySection(tag)

            Text("${"hollowengine.tags.entries".lang}:") {
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
                items(tag.entries.toList()) { entry -> EntryItem(tag, entry) }
            }
        }
    }

    private fun UiScope.TagDetailsHeader(tag: TagData) {
        Row(Grow.Std) {
            Column(Grow.Std) {
                Text(tag.key.toString()) { modifier.font(sizes.largeText).textColor(ColorTheme.UI.WhiteReplacement) }
                Text("${"hollowengine.tags.type".lang}: ${tag.type.name}") {
                    modifier.font(sizes.normalText).textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.7f))
                        .margin(top = Dimensions.PaddingSmall)
                }
            }

            Box {
                val isHovered by modifier.hoverable()
                val bgColor by animateColorAsState(
                    if (isHovered) (if (tag.isDeleted) ColorTheme.Accents.Success else ColorTheme.Console.Error)
                    else ColorTheme.UI.BackgroundElements,
                    tween(easing = Easing.easeOutQuart)
                )

                Button(if (tag.isDeleted) "hollowengine.tags.restore".lang else "hollowengine.tags.delete".lang) {
                    modifier.backgroundColor(bgColor).textColor(ColorTheme.UI.WhiteReplacement)
                        .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingNormal)
                        .onClick { if (tag.isDeleted) restoreTag(tag) else deleteTag(tag) }
                }
            }
        }
    }

    private fun UiScope.AddEntrySection(tag: TagData) {
        Row(Grow.Std) {
            modifier.margin(top = Dimensions.PaddingMedium, bottom = Dimensions.PaddingMedium)
            Box(Grow.Std) {
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
                Button("+ ${"hollowengine.tags.add".lang}") {
                    modifier.backgroundColor(bgColor).textColor(ColorTheme.UI.WhiteReplacement)
                        .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingNormal)
                        .onClick { addEntryToTag(tag, newEntryInput.value); newEntryInput.set("") }
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
        val connection = Minecraft.getInstance().connection ?: return emptyList()

        connection.registryAccess().registry(Registries.BLOCK).ifPresent { registry ->
            registry.tags.forEach { tag ->
                val (tagKey, holders) = tag.first to tag.second
                tags.add(TagData(tagKey.location, RegistryType.BLOCK, MutableStateList<ResourceLocation>().apply {
                    holders.forEach { it.unwrapKey().ifPresent { key -> add(key.location()) } }
                }))
            }
        }
        connection.registryAccess().registry(Registries.ITEM).ifPresent { registry ->
            registry.tags.forEach { tag ->
                val (tagKey, holders) = tag.first to tag.second
                tags.add(TagData(tagKey.location, RegistryType.ITEM, MutableStateList<ResourceLocation>().apply {
                    holders.forEach { it.unwrapKey().ifPresent { key -> add(key.location()) } }
                }))
            }
        }
        return tags
    }

    private fun updateTagStats() {
        val tags = getAllTags()
        tagStats.set(
            TagStats(
                tags.size,
                tags.count { it.type == RegistryType.BLOCK },
                tags.count { it.type == RegistryType.ITEM })
        )
    }

    private fun createNewTag() {
        val name = "custom:new_tag_${System.currentTimeMillis()}".rl
        CUpdateTagPacket(name, name, "ITEM", CUpdateTagPacket.TagAction.CREATE_TAG).send()
    }

    private fun deleteTag(tag: TagData) {
        CUpdateTagPacket(tag.key, tag.key, tag.type.name, CUpdateTagPacket.TagAction.DELETE_TAG).send()
    }

    private fun restoreTag(tag: TagData) {
        CUpdateTagPacket(tag.key, tag.key, tag.type.name, CUpdateTagPacket.TagAction.RESTORE_TAG).send()
    }

    private fun addEntryToTag(tag: TagData, entryString: String) {
        if (entryString.isBlank()) return
        CUpdateTagPacket(tag.key, entryString.rl, tag.type.name, CUpdateTagPacket.TagAction.ADD).send()
    }

    private fun removeEntryFromTag(tag: TagData, entry: ResourceLocation) {
        CUpdateTagPacket(tag.key, entry, tag.type.name, CUpdateTagPacket.TagAction.REMOVE).send()
    }
}
