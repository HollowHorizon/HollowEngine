package ru.hollowhorizon.hollowengine.client.ui.entity

import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTreeItem


internal object PathTree {
    fun rows(
        paths: List<String>,
        expanded: Set<String>,
        query: String = "",
        folderIcon: String,
        fileIcon: (String) -> String,
        selected: String? = null,
    ): List<UiTreeItem<String?>> {
        val matching = if (query.isBlank()) paths else paths.filter { it.contains(query, ignoreCase = true) }
        if (matching.isEmpty()) return emptyList()

        val root = Node("", "")
        matching.forEach { path -> root.add(path) }

        val rows = ArrayList<UiTreeItem<String?>>()
        root.flattenInto(rows, depth = 0, expanded, expandAll = query.isNotBlank(), folderIcon, fileIcon, selected)
        return rows
    }

    private class Node(val id: String, val label: String) {
        val children = LinkedHashMap<String, Node>()
        var path: String? = null

        val isFolder: Boolean get() = children.isNotEmpty()

        fun add(fullPath: String) {
            val namespace = fullPath.substringBefore(':', "")
            val rest = if (namespace.isEmpty()) fullPath else fullPath.substringAfter(':')
            val segments = buildList {
                if (namespace.isNotEmpty()) add(namespace)
                addAll(rest.split('/').filter { it.isNotEmpty() })
            }
            if (segments.isEmpty()) return

            var node = this
            var prefix = ""
            segments.forEachIndexed { index, segment ->
                prefix = if (prefix.isEmpty()) segment else "$prefix/$segment"
                node = node.children.getOrPut(segment) { Node(prefix, segment) }
                if (index == segments.lastIndex) node.path = fullPath
            }
        }

        fun flattenInto(
            rows: MutableList<UiTreeItem<String?>>,
            depth: Int,
            expanded: Set<String>,
            expandAll: Boolean,
            folderIcon: String,
            fileIcon: (String) -> String,
            selected: String?,
        ) {
            children.values.sortedWith(compareByDescending<Node> { it.isFolder }.thenBy { it.label.lowercase() })
                .forEach { child ->
                    val open = expandAll || child.id in expanded
                    rows += UiTreeItem(
                        id = child.id,
                        label = child.label,
                        depth = depth,
                        payload = child.path.takeUnless { child.isFolder },
                        icon = if (child.isFolder) folderIcon else fileIcon(child.path.orEmpty()),
                        hasChildren = child.isFolder,
                        expanded = open,
                        selected = child.path != null && child.path == selected,
                    )
                    if (child.isFolder && open) {
                        child.flattenInto(rows, depth + 1, expanded, expandAll, folderIcon, fileIcon, selected)
                    }
                }
        }
    }
}
