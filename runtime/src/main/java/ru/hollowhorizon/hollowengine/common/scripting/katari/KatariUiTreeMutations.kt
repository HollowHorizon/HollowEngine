package ru.hollowhorizon.hollowengine.common.scripting.katari

import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlTree

internal fun normalizeUiTarget(target: String): String = target.removePrefix(".").removePrefix("#")

internal fun UiXmlTree.insertIntoFirst(
    target: String,
    child: UiXmlTree,
    markInserted: () -> Unit,
): UiXmlTree {
    if (matchesTarget(target)) {
        markInserted()
        return copy(children = children + child)
    }
    return updateFirstChild(target) { current, mark ->
        current.insertIntoFirst(target, child) {
            mark()
            markInserted()
        }
    } ?: this
}

internal fun UiXmlTree.replaceChildrenFirst(
    target: String,
    children: List<UiXmlTree>,
    markReplaced: () -> Unit,
): UiXmlTree {
    if (matchesTarget(target)) {
        markReplaced()
        children.singleOrNull()?.let {
            return it
        }
        return copy(children = children)
    }
    return updateFirstChild(target) { current, mark ->
        current.replaceChildrenFirst(target, children) {
            mark()
            markReplaced()
        }
    } ?: this
}

internal fun UiXmlTree.modifyFirst(
    target: String,
    attributes: Map<String, String>,
    markModified: () -> Unit,
): UiXmlTree {
    if (matchesTarget(target)) {
        markModified()
        return copy(attributes = this.attributes + attributes)
    }
    return updateFirstChild(target) { current, mark ->
        current.modifyFirst(target, attributes) {
            mark()
            markModified()
        }
    } ?: this
}

internal fun UiXmlTree.modifyAll(
    target: String,
    attributes: Map<String, String>,
): UiMutationResult {
    var count = 0
    val next = modifyAllMatching(target) {
        count++
        copy(attributes = this.attributes + attributes)
    }
    return UiMutationResult(next, count)
}

internal fun UiXmlTree.removeAttributeFirst(
    target: String,
    attribute: String,
    markModified: () -> Unit,
): UiXmlTree {
    if (matchesTarget(target)) {
        markModified()
        return copy(attributes = attributes - attribute)
    }
    return updateFirstChild(target) { current, mark ->
        current.removeAttributeFirst(target, attribute) {
            mark()
            markModified()
        }
    } ?: this
}

private fun UiXmlTree.updateFirstChild(
    target: String,
    update: (UiXmlTree, () -> Unit) -> UiXmlTree,
): UiXmlTree? {
    var changed = false
    val nextChildren = children.map { current ->
        if (changed) {
            current
        } else {
            update(current) { changed = true }
        }
    }
    return if (changed) copy(children = nextChildren) else null
}

private fun UiXmlTree.modifyAllMatching(
    target: String,
    update: UiXmlTree.() -> UiXmlTree,
): UiXmlTree {
    val current = if (matchesTarget(target)) update() else this
    val nextChildren = current.children.map { child -> child.modifyAllMatching(target, update) }
    return if (nextChildren == current.children) current else current.copy(children = nextChildren)
}

internal fun UiXmlTree.withAttributes(attributes: Map<String, String>): UiXmlTree {
    if (attributes.isEmpty()) return this
    return copy(attributes = this.attributes + attributes)
}

internal fun UiXmlTree.matchesTarget(target: String): Boolean {
    if (name.equals(target, ignoreCase = true)) return true
    if (attributes["id"]?.removePrefix("#") == target) return true
    return tagAttributes().any { it == target }
}

private fun UiXmlTree.tagAttributes(): List<String> {
    return listOfNotNull(attributes["tag"], attributes["tags"], attributes["class"])
        .flatMap { it.split(Regex("\\s+")) }
        .map { it.removePrefix(".") }
        .filter { it.isNotBlank() }
}

internal data class UiMutationResult(
    val root: UiXmlTree,
    val count: Int,
)
