package ru.hollowhorizon.hollowengine.client.gui.scripting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.IconHelper
import ru.hollowhorizon.hollowengine.client.ui.docking.DockItem
import ru.hollowhorizon.hollowengine.client.ui.normalizeEditorLineEndings
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTreeItem
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import java.io.File
import java.util.Base64

private const val BinarySampleSize = 8192
private const val AutoSaveDelayMillis = 900L

internal class HollowIdeModel {
    val tree = HollowIdeFileTree()
    val files = mutableStateMapOf<String, HollowIdeOpenFile>()
    var selectedTreePath by mutableStateOf("")
        private set
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingSaves = mutableMapOf<String, Job>()

    fun visibleTreeItems(filter: String): List<UiTreeItem<HollowIdeFileNode>> {
        return tree.visible(filter).map { node ->
            UiTreeItem(
                id = node.path.ifEmpty { "root" }.replace('/', '-'),
                label = node.name,
                depth = node.depth,
                payload = node,
                icon = IconHelper.forPath(node.path, node.isDirectory, node.expanded).toString(),
                hasChildren = node.isDirectory,
                expanded = node.expanded,
                selected = node.path == selectedTreePath,
            )
        }
    }

    fun toggle(node: HollowIdeFileNode) {
        selectedTreePath = node.path
        tree.toggle(node.path)
    }

    fun open(node: HollowIdeFileNode): HollowIdeOpenResult {
        selectedTreePath = node.path
        if (node.isDirectory) {
            tree.toggle(node.path)
            return HollowIdeOpenResult.Directory
        }
        return openFile(node.path)
    }

    fun openFile(path: String): HollowIdeOpenResult {
        selectedTreePath = path
        val file = path.fromReadablePath()
        if (!file.isFile || file.isProbablyBinary()) return HollowIdeOpenResult.Unsupported
        files[path]?.let { opened ->
            if (!opened.dirty) opened.refresh(file.readText().normalizeEditorLineEndings())
            return HollowIdeOpenResult.File(opened, created = false)
        }
        val opened = HollowIdeOpenFile(path, file.readText().normalizeEditorLineEndings())
        files[path] = opened
        return HollowIdeOpenResult.File(opened, created = true)
    }

    fun openReadOnly(path: String, text: String): HollowIdeOpenFile {
        files[path]?.let { opened ->
            opened.refreshReadOnly(text)
            return opened
        }
        return HollowIdeOpenFile(path, text.normalizeEditorLineEndings(), readOnly = true).also { opened ->
            files[path] = opened
        }
    }

    fun updateText(path: String, text: String) {
        val file = files[path]?.takeUnless { it.readOnly } ?: return
        file.update(text)
        scheduleSave(path)
    }

    fun save(path: String): Boolean {
        val file = files[path]?.takeUnless { it.readOnly } ?: return false
        pendingSaves.remove(path)?.cancel()
        path.fromReadablePath().writeText(file.text)
        file.markSaved()
        tree.refresh()
        return true
    }

    fun saveAll(): Int {
        var count = 0
        files.values.forEach { file ->
            if (file.dirty && save(file.path)) count++
        }
        return count
    }

    private fun scheduleSave(path: String) {
        val file = files[path]?.takeIf { it.dirty } ?: return
        val text = file.text
        pendingSaves.remove(path)?.cancel()
        pendingSaves[path] = saveScope.launch {
            delay(AutoSaveDelayMillis)
            runCatching {
                path.fromReadablePath().writeText(text)
            }.onSuccess {
                Minecraft.getInstance().execute {
                    pendingSaves.remove(path)
                    files[path]?.markSavedIfText(text)
                    tree.refresh()
                }
            }
        }
    }
}

internal sealed interface HollowIdeOpenResult {
    data object Directory : HollowIdeOpenResult
    data object Unsupported : HollowIdeOpenResult
    data class File(val file: HollowIdeOpenFile, val created: Boolean) : HollowIdeOpenResult
}

internal class HollowIdeOpenFile(
    val path: String,
    initialText: String,
    val readOnly: Boolean = false,
) {
    var text by mutableStateOf(initialText.normalizeEditorLineEndings())
        private set
    var dirty by mutableStateOf(false)
        private set

    val id: String = fileDockItemId(path)
    val title: String get() = path.substringAfterLast('/').ifEmpty { path }

    fun dockItem(): DockItem {
        return DockItem(id, title, IconHelper.forPath(path).toString(), dirty = dirty && !readOnly)
    }

    fun update(next: String) {
        if (readOnly) return
        val normalized = next.normalizeEditorLineEndings()
        if (text == normalized) return
        text = normalized
        dirty = true
    }

    fun markSaved() {
        if (readOnly) return
        dirty = false
    }

    fun markSavedIfText(savedText: String) {
        if (readOnly) return
        if (text == savedText) dirty = false
    }

    fun refresh(next: String) {
        if (readOnly) return
        val normalized = next.normalizeEditorLineEndings()
        if (dirty || text == normalized) return
        text = normalized
    }

    fun refreshReadOnly(next: String) {
        if (!readOnly) return
        val normalized = next.normalizeEditorLineEndings()
        if (text == normalized) return
        text = normalized
        dirty = false
    }
}

internal class HollowIdeFileTree {
    private val root = HollowIdeFileNode("HollowEngine", "", depth = 0, isDirectory = true).apply {
        expanded = true
    }

    init {
        refresh()
    }

    fun refresh() {
        root.refresh()
    }

    fun toggle(path: String) {
        val node = root.find(path) ?: return
        if (!node.isDirectory) return
        node.expanded = !node.expanded
        if (node.expanded) node.refresh()
    }

    fun visible(filter: String): List<HollowIdeFileNode> {
        val cleanFilter = filter.trim()
        return root.visible(cleanFilter)
    }
}

internal class HollowIdeFileNode(
    val name: String,
    val path: String,
    val depth: Int,
    val isDirectory: Boolean,
) {
    val children = mutableStateListOf<HollowIdeFileNode>()
    var expanded by mutableStateOf(false)

    fun refresh() {
        if (!isDirectory) return
        val previous = children.associateBy({ it.path }, { it.expanded })
        val files = path.fromReadablePath().listFiles().orEmpty()
        children.clear()
        children += files
            .sortedWith(compareBy<File> { it.isFile }.thenBy { it.name.lowercase() })
            .map { file ->
                val childPath = if (path.isEmpty()) file.name else "$path/${file.name}"
                HollowIdeFileNode(file.name, childPath, depth + 1, file.isDirectory).apply {
                    expanded = previous[childPath] == true
                    if (expanded) refresh()
                }
            }
    }

    fun find(targetPath: String): HollowIdeFileNode? {
        if (path == targetPath) return this
        return children.firstNotNullOfOrNull { it.find(targetPath) }
    }

    fun visible(filter: String): List<HollowIdeFileNode> {
        if (filter.isNotEmpty() && !matches(filter)) return emptyList()
        val result = mutableListOf(this)
        if (expanded || filter.isNotEmpty()) {
            children.forEach { result += it.visible(filter) }
        }
        return result
    }

    private fun matches(filter: String): Boolean {
        return path.contains(filter, ignoreCase = true) || children.any { it.matches(filter) }
    }
}

internal fun fileDockItemId(path: String): String {
    return "ide-file-" + Base64.getUrlEncoder().withoutPadding().encodeToString(path.toByteArray())
}

private fun File.isProbablyBinary(): Boolean {
    val bytes = inputStream().use { it.readNBytes(BinarySampleSize) }
    if (bytes.isEmpty()) return false
    val nullBytes = bytes.count { it.toInt() == 0 }
    val controlBytes = bytes.count { byte ->
        val value = byte.toInt() and 0xFF
        value < 32 && value != 9 && value != 10 && value != 13
    }
    return nullBytes > bytes.size / 100 || controlBytes > bytes.size / 10
}
