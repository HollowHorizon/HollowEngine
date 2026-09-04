@file:OptIn(ExperimentalSerializationApi::class)

package ru.hollowhorizon.hollowengine.client.ui.entity

import androidx.compose.runtime.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextFieldMode

@Composable
internal fun EntitySidebar(session: EntityEditorSession, width: Float) {
    Column(
        tags = listOf("ee-sidebar"),
        modifier = Modifier.size(width.px, 100.percent),
    ) {
        SidebarHeader(session)
        SidebarTabs(session)
        if (session.searchOpen) SidebarSearch(session)

        Column(
            tags = listOf("ee-sidebar-body"),
            modifier = Modifier.size(100.percent, 0.px).grow(1f).scrollable(horizontal = false),
        ) {
            when (session.tab) {
                EntityEditorTab.COMPONENTS -> ComponentsTab(session)
                EntityEditorTab.SCRIPTS -> ScriptsTab(session)
            }
        }

        if (session.tab == EntityEditorTab.COMPONENTS) AddComponentFooter(session)
    }
}

@Composable
private fun SidebarHeader(session: EntityEditorSession) {
    Row(tags = listOf("ee-header")) {
        Text(EntityEditorLang.title, tags = listOf("ee-header-title"), modifier = Modifier.grow(1f))
        if (session.isBusy) BusySpinner()
        EditorIconButton(EntityEditorIcons.SEARCH, EntityEditorLang.search) {
            session.searchOpen = !session.searchOpen
            if (!session.searchOpen) session.query = ""
        }
        EditorIconButton(EntityEditorIcons.RELOAD, EntityEditorLang.refresh) { session.requestRefresh() }
    }
}

@Composable
private fun SidebarTabs(session: EntityEditorSession) {
    Row(tags = listOf("ee-tabs")) {
        Tab(EntityEditorLang.components, session.tab == EntityEditorTab.COMPONENTS) {
            session.tab = EntityEditorTab.COMPONENTS
        }
        Tab(EntityEditorLang.scripts, session.tab == EntityEditorTab.SCRIPTS) {
            session.tab = EntityEditorTab.SCRIPTS
        }
    }
}

@Composable
private fun Tab(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        tags = if (active) listOf("ee-tab", "active") else listOf("ee-tab"),
        modifier = Modifier.grow(1f).input(hoverable = true, clickable = true).cursor(UiCursorShape.HAND)
            .onClick { onClick() },
    ) {
        Text(label, tags = listOf("ee-tab-label"))
    }
}

@Composable
private fun SidebarSearch(session: EntityEditorSession) {
    Row(tags = listOf("ee-search")) {
        Image(EntityEditorIcons.SEARCH, tags = listOf("ee-search-icon"))
        TextField(
            value = session.query,
            id = EntityEditorSearchInput,
            mode = UiTextFieldMode.SINGLE_LINE,
            placeholder = EntityEditorLang.searchHint,
            fontSize = 9f,
            onChange = { session.query = it },
            tags = listOf("ee-input", "flat"),
            modifier = Modifier.grow(1f),
        )
        if (session.query.isNotEmpty()) {
            EditorIconButton(EntityEditorIcons.CLOSE, EntityEditorLang.search) { session.query = "" }
        }
    }
}

@Composable
private fun ComponentsTab(session: EntityEditorSession) {
    val query = session.query
    val entries = session.entries.filter { entry -> matchesComponent(entry, query) }

    if (session.hasSlots) {
        EditorButton(
            label = EntityEditorLang.inventory,
            icon = EntityEditorIcons.COMPONENT,
            modifier = Modifier.size(100.percent, UiLength.Fit),
        ) { session.openSlots() }
    }

    if (entries.isEmpty()) {
        Text(EntityEditorLang.nothingFound, tags = listOf("ee-hint"))
        return
    }

    entries.forEach { entry ->
        key(entry.id.toString()) { ComponentCard(session, entry, query) }
    }
}

private fun matchesComponent(entry: ComponentEntry, query: String): Boolean {
    if (query.isBlank()) return true
    val descriptor = entry.serializer.descriptor
    val name = ComponentLabels.componentName(entry.id, descriptor)
    if (name.contains(query, ignoreCase = true) || entry.id.toString().contains(query, ignoreCase = true)) return true
    return (0 until descriptor.elementsCount).any { index ->
        ComponentLabels.fieldName(entry.id, descriptor, index)
            .contains(query, ignoreCase = true) || descriptor.getElementName(index).contains(query, ignoreCase = true)
    }
}

@Composable
private fun ComponentCard(session: EntityEditorSession, entry: ComponentEntry, query: String) {
    val descriptor = entry.serializer.descriptor
    if (ComponentLabels.isHidden(descriptor)) return

    var expanded by remember(entry.id) { mutableStateOf(false) }
    var draft by remember(entry.id) { mutableStateOf<JsonObject?>(null) }

    val document = draft ?: entry.json ?: return
    val name = ComponentLabels.componentName(entry.id, descriptor)
    val icon = ComponentLabels.componentIcon(descriptor) ?: EntityEditorIcons.COMPONENT
    val nameMatches = query.isBlank() || name.contains(query, ignoreCase = true) || entry.id.toString()
        .contains(query, ignoreCase = true)

    val open = expanded || query.isNotBlank()

    val apply: (JsonObject) -> Unit = { edited ->
        val decoded = ComponentJson.decode(entry.serializer, edited)
        if (decoded == null) {
            draft = edited
        } else {
            draft = null
            session.update(entry, decoded)
        }
    }

    Column(tags = if (entry.virtual) listOf("ee-card", "virtual") else listOf("ee-card")) {
        Row(
            tags = listOf("ee-card-head"),
            modifier = Modifier.input(hoverable = true, clickable = true).cursor(UiCursorShape.HAND)
                .onClick { expanded = !expanded },
        ) {
            Image(icon, tags = listOf("ee-card-icon"))
            Text(name, tags = listOf("ee-card-title"), modifier = Modifier.grow(1f))
            if (entry.virtual) {
                Text(EntityEditorLang.virtual, tags = listOf("ee-badge"))
            } else {
                EditorIconButton(EntityEditorIcons.REMOVE, EntityEditorLang.remove, tags = listOf("ee-card-remove")) {
                    session.remove(entry)
                }
            }
            EditorArrow(open)
        }

        if (!open) return@Column

        ComponentLabels.componentDescription(descriptor)?.let { Text(it, tags = listOf("ee-hint")) }
        if (entry.virtual) Text(EntityEditorLang.virtualHint, tags = listOf("ee-hint"))

        val extras = ComponentEditors.of(entry.id)
        val scope = remember(entry.id, document) { ComponentEditorScope(entry, document, apply) }

        Column(tags = listOf("ee-card-body")) {
            if (extras != null && extras.before) extras.content(scope)
            if (extras == null || !extras.replacesFields) {
                ComponentFields(
                    owner = entry.id,
                    descriptor = descriptor,
                    value = document,
                    path = "/${entry.id}",
                    query = if (nameMatches) "" else query,
                    onChange = apply,
                )
            }
            if (extras != null && !extras.before) extras.content(scope)
        }
    }
}

@Composable
private fun AddComponentFooter(session: EntityEditorSession) {
    var open by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }

    Column(tags = listOf("ee-footer")) {
        if (open) {
            val candidates = session.addable.filter { descriptor ->
                filter.isBlank() || descriptor.id.toString()
                    .contains(filter, ignoreCase = true) || ComponentLabels.componentName(
                    descriptor.id,
                    descriptor.serializer.descriptor
                ).contains(filter, ignoreCase = true)
            }

            Column(tags = listOf("ee-add-panel")) {
                TextField(
                    value = filter,
                    id = "ee-add-filter",
                    placeholder = EntityEditorLang.searchHint,
                    fontSize = 9f,
                    onChange = { filter = it },
                    tags = listOf("ee-input"),
                    modifier = Modifier.size(100.percent, 22.px),
                )
                Column(
                    tags = listOf("ee-add-list"),
                    modifier = Modifier.size(100.percent, 160.px).scrollable(horizontal = false),
                ) {
                    candidates.forEach { descriptor ->
                        key(descriptor.id.toString()) {
                            val label = ComponentLabels.componentName(descriptor.id, descriptor.serializer.descriptor)
                            Row(
                                tags = listOf("ee-add-row"),
                                modifier = Modifier.input(hoverable = true, clickable = true).cursor(UiCursorShape.HAND)
                                    .onClick {
                                        session.add(descriptor)
                                        open = false
                                        filter = ""
                                    },
                            ) {
                                Image(
                                    ComponentLabels.componentIcon(descriptor.serializer.descriptor)
                                        ?: EntityEditorIcons.COMPONENT,
                                    tags = listOf("ee-add-icon"),
                                )
                                Column(modifier = Modifier.grow(1f)) {
                                    Text(label, tags = listOf("ee-add-label"))
                                    Text(descriptor.id.toString(), tags = listOf("ee-add-id"))
                                }
                            }
                        }
                    }
                    if (candidates.isEmpty()) Text(EntityEditorLang.allAdded, tags = listOf("ee-hint"))
                }
            }
        }

        EditorButton(
            label = EntityEditorLang.addComponent,
            icon = EntityEditorIcons.ADD,
            tags = listOf("primary"),
            modifier = Modifier.size(100.percent, 30.px),
        ) { open = !open }
    }
}

@Composable
private fun ScriptsTab(session: EntityEditorSession) {
    val query = session.query
    val attached = session.attachedScripts.filter { query.isBlank() || it.contains(query, ignoreCase = true) }

    Text(EntityEditorLang.attached, tags = listOf("ee-section-title"))
    if (attached.isEmpty()) {
        Text(EntityEditorLang.noScripts, tags = listOf("ee-hint"))
    } else {
        attached.forEach { path ->
            key(path) {
                ScriptRow(path, EntityEditorIcons.REMOVE, EntityEditorLang.detach) { session.detachScript(path) }
            }
        }
    }

    EditorButton(
        label = EntityEditorLang.attach,
        icon = EntityEditorIcons.ADD,
        tags = listOf("primary"),
        modifier = Modifier.size(100.percent, UiLength.Fit),
    ) {
        session.pendingPicker = AssetPickerRequest(
            title = EntityEditorLang.attach,
            candidates = session.availableScripts,
            current = "",
            onPick = session::attachScript,
        )
    }

    if (session.availableScripts.isEmpty()) {
        Text(EntityEditorLang.noSuitableScripts, tags = listOf("ee-hint"))
    }
}

@Composable
private fun ScriptRow(path: String, icon: String, tooltip: String, onClick: () -> Unit) {
    Row(tags = listOf("ee-script-row")) {
        Image(EntityEditorIcons.SCRIPT, tags = listOf("ee-script-icon"))
        Column(modifier = Modifier.grow(1f)) {
            Text(path.substringAfterLast('/'), tags = listOf("ee-script-name"))
            Text(path, tags = listOf("ee-script-path"))
        }
        EditorIconButton(icon, tooltip, onClick = onClick)
    }
}
