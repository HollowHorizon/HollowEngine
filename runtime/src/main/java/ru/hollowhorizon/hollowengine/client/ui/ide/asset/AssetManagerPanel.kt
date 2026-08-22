package ru.hollowhorizon.hollowengine.client.ui.ide.asset

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.server.packs.resources.ResourceManager
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTreeFilterState
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTreeItem
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTreeView
import ru.hollowhorizon.hollowengine.client.ui.widgets.tooltipOnHover
import ru.hollowhorizon.hollowengine.client.utils.lang

internal const val FolderIcon = "hollowengine:textures/gui/icons/folder.svg"
private const val OpenFolderIcon = "hollowengine:textures/gui/icons/folder_open.svg"
private const val ReloadIcon = "hollowengine:textures/gui/icons/reload.svg"
private const val SearchIcon = "hollowengine:textures/gui/icons/search.svg"
private const val CloseIcon = "hollowengine:textures/gui/icons/cross.svg"

@Composable
internal fun AssetManagerPanel(
    onOpenFile: (AssetResourceScope, AssetFile, ByteArray?, Boolean) -> Unit,
    onFocusFilter: (String) -> Unit,
) {
    var scope by remember { mutableStateOf(AssetResourceScope.CLIENT) }
    var sidebarWidth by remember { mutableStateOf(DefaultSidebarWidth) }
    var refreshRevision by remember { mutableIntStateOf(0) }
    var remoteConnection by remember { mutableStateOf<Any?>(null) }
    var remoteRefreshRevision by remember { mutableIntStateOf(-1) }
    var remoteLifecycleRevision by remember { mutableIntStateOf(-1) }
    val indexes = remember { mutableStateMapOf<AssetResourceScope, AssetIndex>() }
    val localLoading = remember { mutableStateMapOf<AssetResourceScope, Boolean>() }
    val localErrors = remember { mutableStateMapOf<AssetResourceScope, String>() }
    val localLoadTokens = remember { mutableMapOf<AssetResourceScope, Any>() }
    val selectedDirectories = remember { mutableStateMapOf<AssetResourceScope, String>() }
    val expandedDirectories = remember { mutableStateMapOf<String, Boolean>() }
    var selectedEntryKey by remember { mutableStateOf<String?>(null) }
    var contextMenu by remember { mutableStateOf<AssetContextMenu?>(null) }
    val treeClicks = remember { AssetClickTracker() }
    val gridClicks = remember { AssetClickTracker() }
    val treeFilter = remember { UiTreeFilterState("asset-tree-filter") }
    val gridFilter = remember { UiTreeFilterState("asset-grid-filter") }

    LaunchedEffect(gridFilter.expanded) {
        if (gridFilter.expanded) onFocusFilter(gridFilter.inputId)
    }

    val minecraft = Minecraft.getInstance()
    val lifecycleRevision = when (scope) {
        AssetResourceScope.CLIENT -> AssetManagerLifecycle.clientRevision
        AssetResourceScope.SERVER -> AssetManagerLifecycle.serverRevision
    }
    val resourceManager = assetResourceManager(scope)
    val connection = minecraft.connection
    val remoteServer = scope == AssetResourceScope.SERVER && resourceManager == null && connection != null

    LaunchedEffect(scope, resourceManager, connection, refreshRevision, lifecycleRevision) {
        val requestedScope = scope
        val requestedManager = resourceManager
        if (remoteServer) {
            localLoadTokens.remove(requestedScope)
            localLoading.remove(requestedScope)
            indexes.remove(requestedScope)
            if (
                remoteConnection !== connection ||
                remoteRefreshRevision != refreshRevision ||
                remoteLifecycleRevision != lifecycleRevision
            ) {
                RemoteServerAssetState.reset()
                remoteConnection = connection
                remoteRefreshRevision = refreshRevision
                remoteLifecycleRevision = lifecycleRevision
            }
            localErrors.remove(requestedScope)
            RemoteServerAssetState.requestRoot()
            return@LaunchedEffect
        }
        if (requestedManager == null) {
            localLoadTokens.remove(requestedScope)
            indexes.remove(requestedScope)
            localErrors[requestedScope] = AssetManagerLang.CONNECT_TO_SERVER.lang
            localLoading.remove(requestedScope)
            return@LaunchedEffect
        }
        val loadToken = Any()
        localLoadTokens[requestedScope] = loadToken
        localLoading[requestedScope] = true
        localErrors.remove(requestedScope)
        try {
            val loaded = withContext(Dispatchers.Default) {
                AssetIndex.load(requestedManager, requestedScope.packType)
            }
            if (localLoadTokens[requestedScope] === loadToken) indexes[requestedScope] = loaded
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            if (localLoadTokens[requestedScope] === loadToken) {
                indexes.remove(requestedScope)
                val detail = failure.message ?: failure::class.simpleName.orEmpty()
                localErrors[requestedScope] = AssetManagerLang.INDEX_FAILED.lang(detail)
            }
        } finally {
            if (localLoadTokens[requestedScope] === loadToken) {
                localLoadTokens.remove(requestedScope)
                localLoading.remove(requestedScope)
            }
        }
    }

    val remoteRevision = RemoteServerAssetState.revision
    val index = if (remoteServer) {
        remember(remoteRevision) { RemoteServerAssetState.snapshot() }
    } else {
        indexes[scope] ?: AssetIndex.Empty
    }
    LaunchedEffect(scope, index.directoryKeys) {
        val selected = index.directory(selectedDirectories[scope]) ?: index.rootDirectories.firstOrNull()
        if (selected != null) {
            selectedDirectories[scope] = selected.key
            expandedDirectories[scope.expandedKey(selected.key)] = true
            if (remoteServer) RemoteServerAssetState.requestDirectory(selected)
        }
    }

    val selectedDirectory = index.directory(selectedDirectories[scope])
    val expandedKeys = index.visibleExpandedKeys(scope, expandedDirectories)
    val loading = if (remoteServer) RemoteServerAssetState.loading else localLoading[scope] == true
    val error = if (remoteServer) RemoteServerAssetState.error ?: localErrors[scope] else localErrors[scope]

    fun selectDirectory(directory: AssetDirectory) {
        selectedDirectories[scope] = directory.key
        selectedEntryKey = null
        if (remoteServer) RemoteServerAssetState.requestDirectory(directory)
    }

    fun openDirectory(directory: AssetDirectory) {
        directory.ancestorKeys().forEach { key ->
            expandedDirectories[scope.expandedKey(key)] = true
        }
        selectDirectory(directory)
    }

    fun openFile(file: AssetFile, forceText: Boolean = false) {
        val requestedScope = scope
        localErrors.remove(requestedScope)
        if (!remoteServer) {
            onOpenFile(requestedScope, file, null, forceText)
            return
        }
        RemoteServerAssetState.requestFile(file) { bytes, requestError ->
            if (requestError != null) {
                localErrors[requestedScope] = requestError
            } else {
                onOpenFile(requestedScope, file, bytes, forceText)
            }
        }
    }

    Row(
        tags = listOf("ide-panel", "asset-manager-root"),
        modifier = Modifier.style("hollowengine:ui/styles/asset-manager.hss").size(100.percent, 100.percent),
    ) {
        Column(tags = listOf("asset-sidebar"), modifier = Modifier.size(sidebarWidth.px, 100.percent)) {
            AssetScopeTabs(scope) { next ->
                scope = next
                selectedEntryKey = null
                contextMenu = null
                localErrors.remove(next)
            }
            UiTreeView(
                items = index.visibleDirectories(expandedKeys, treeFilter.query).map { directory ->
                    val expanded = directory.key in expandedKeys
                    UiTreeItem(
                        id = "asset-${scope.name.lowercase()}-${directory.key}",
                        label = directory.name,
                        depth = directory.depth,
                        payload = directory,
                        icon = if (expanded) OpenFolderIcon else FolderIcon,
                        hasChildren = index.hasChildDirectories(directory),
                        expanded = expanded,
                        selected = selectedDirectory == directory,
                    )
                },
                onToggle = { item ->
                    val key = scope.expandedKey(item.payload.key)
                    val expanding = expandedDirectories[key] != true
                    expandedDirectories[key] = expanding
                    if (expanding && remoteServer) RemoteServerAssetState.requestDirectory(item.payload)
                },
                onSelect = { item, event ->
                    if (!event.isLeftClick()) return@UiTreeView
                    selectDirectory(item.payload)
                    if (treeClicks.isDoubleClick(item.id)) {
                        val key = scope.expandedKey(item.payload.key)
                        expandedDirectories[key] = expandedDirectories[key] != true
                    }
                    event.consume()
                },
                filterState = treeFilter,
                onFilterOpened = onFocusFilter,
            )
        }

        AssetSidebarSplitter(sidebarWidth) { sidebarWidth = it }

        Column(
            tags = listOf("asset-content"),
            modifier = Modifier.size(0.px, 100.percent).grow(1f).focus()
                .onKeyInput(FilterShortcutPriority) { input ->
                    if (input.repeat || !input.command || input.key != GLFW.GLFW_KEY_F) return@onKeyInput
                    gridFilter.open()
                    input.consume()
                },
        ) {
            AssetToolbar(
                scope = scope,
                directory = selectedDirectory,
                fileCount = index.files.size,
                namespaceCount = index.namespaceCount,
                loading = loading,
                remote = remoteServer,
                filter = gridFilter,
                onRefresh = { refreshRevision++ },
            )
            when {
                error != null -> AssetMessage(error.lang)
                loading && index.directoryKeys.isEmpty() -> AssetMessage(AssetManagerLang.LOADING.lang)
                selectedDirectory == null -> AssetMessage(AssetManagerLang.NO_RESOURCES.lang)
                else -> AssetGrid(
                    scope = scope,
                    entries = index.children(selectedDirectory).filter { entry ->
                        val query = gridFilter.query.trim()
                        query.isEmpty() || entry.name.contains(query, ignoreCase = true)
                    },
                    selectedEntryKey = selectedEntryKey,
                    clicks = gridClicks,
                    onSelect = { entry -> selectedEntryKey = entry.entryKey },
                    onOpen = { entry ->
                        when (entry) {
                            is AssetGridEntry.Directory -> openDirectory(entry.directory)
                            is AssetGridEntry.File -> openFile(entry.file)
                        }
                    },
                    onContext = { entry, x, y ->
                        selectedEntryKey = entry.entryKey
                        contextMenu = AssetContextMenu(entry, x, y)
                    },
                )
            }
        }

        AssetEntryContextMenu(
            menu = contextMenu,
            onOpen = { entry ->
                contextMenu = null
                when (entry) {
                    is AssetGridEntry.Directory -> openDirectory(entry.directory)
                    is AssetGridEntry.File -> openFile(entry.file)
                }
            },
            onOpenAsText = { file ->
                contextMenu = null
                openFile(file, forceText = true)
            },
            onDismiss = { contextMenu = null },
        )
    }
}

@Composable
private fun AssetScopeTabs(selected: AssetResourceScope, onSelect: (AssetResourceScope) -> Unit) {
    Row(tags = listOf("asset-tabs")) {
        AssetResourceScope.entries.forEach { scope ->
            Box(
                tags = if (scope == selected) listOf("asset-tab", "selected") else listOf("asset-tab"),
                modifier = Modifier.size(0.px, 17.px).grow(1f).cursor(UiCursorShape.HAND).onClick { event ->
                    if (event.isLeftClick()) onSelect(scope)
                    event.consume()
                },
            ) { Text(scope.labelKey.lang, tags = listOf("asset-tab-label")) }
        }
    }
}

@Composable
private fun AssetToolbar(
    scope: AssetResourceScope,
    directory: AssetDirectory?,
    fileCount: Int,
    namespaceCount: Int,
    loading: Boolean,
    remote: Boolean,
    filter: UiTreeFilterState,
    onRefresh: () -> Unit,
) {
    Row(tags = listOf("asset-toolbar")) {
        if (filter.expanded) {
            Image(SearchIcon, tags = listOf("asset-filter-icon"))
            TextField(
                value = filter.query,
                placeholder = AssetManagerLang.FILTER_CURRENT_FOLDER.lang,
                onChange = { filter.query = it },
                id = filter.inputId,
                tags = listOf("asset-filter-input"),
                modifier = Modifier.grow(1f).onKeyInput(FilterShortcutPriority) { input ->
                    if (input.key != GLFW.GLFW_KEY_ESCAPE) return@onKeyInput
                    filter.close()
                    input.consume()
                },
            )
            Image(
                CloseIcon,
                tags = listOf("asset-filter-close"),
                modifier = Modifier.cursor(UiCursorShape.HAND).onClick { event ->
                    filter.close()
                    event.consume()
                }.tooltipOnHover(AssetManagerLang.CLOSE_FILTER.lang),
            )
        } else {
            Text(
                directory?.let { "${scope.directory}/${it.namespace}/${it.path}".trimEnd('/') } ?: scope.directory,
                tags = listOf("asset-breadcrumb"),
                modifier = Modifier.size(0.px, UiLength.Fit).grow(1f).align(vertical = UiAlign.CENTER).textWrap(false),
            )
            Text(
                if (remote) {
                    AssetManagerLang.REMOTE_COUNTS.lang(namespaceCount, fileCount)
                } else {
                    AssetManagerLang.LOCAL_COUNTS.lang(namespaceCount, fileCount)
                },
                tags = listOf("asset-count"),
            )
        }
        Box(
            tags = if (loading) listOf("asset-refresh", "loading") else listOf("asset-refresh"),
            modifier = Modifier.cursor(UiCursorShape.HAND).onClick { event ->
                if (event.isLeftClick() && !loading) onRefresh()
                event.consume()
            }.tooltipOnHover(AssetManagerLang.REFRESH.lang),
        ) { Image(ReloadIcon, tags = listOf("asset-refresh-icon")) }
    }
}

@Composable
private fun AssetSidebarSplitter(width: Float, onWidthChange: (Float) -> Unit) {
    val dragStart = remember { floatArrayOf(width) }
    Box(
        tags = listOf("asset-splitter"),
        modifier = Modifier.size(4.px, 100.percent).input(hoverable = true, draggable = true)
            .cursor(UiCursorShape.RESIZE_HORIZONTAL).onPress { dragStart[0] = width }.onDrag { event ->
                onWidthChange((dragStart[0] + event.dragTotalX).coerceIn(MinSidebarWidth, MaxSidebarWidth))
                event.consume()
            },
    )
}

internal fun assetResourceManager(scope: AssetResourceScope): ResourceManager? {
    val minecraft = Minecraft.getInstance()
    return when (scope) {
        AssetResourceScope.CLIENT -> minecraft.resourceManager
        AssetResourceScope.SERVER -> minecraft.singleplayerServer?.resourceManager
    }
}

private fun AssetResourceScope.expandedKey(directoryKey: String): String = "$name:$directoryKey"

private fun AssetIndex.visibleExpandedKeys(
    scope: AssetResourceScope,
    expanded: Map<String, Boolean>,
): Set<String> = directoryKeys.filterTo(mutableSetOf()) { expanded[scope.expandedKey(it)] == true }

private const val DefaultSidebarWidth = 230f
private const val MinSidebarWidth = 170f
private const val MaxSidebarWidth = 460f
private const val FilterShortcutPriority = 100
