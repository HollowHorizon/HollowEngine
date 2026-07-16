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
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.isModelEditorFile
import ru.hollowhorizon.hollowengine.client.ui.docking.DockItem
import ru.hollowhorizon.hollowengine.client.ui.widgets.normalizeEditorLineEndings
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTreeItem
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import java.io.File
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64

private const val BinarySampleSize = 8192
private const val AutoSaveDelayMillis = 900L

internal class HollowIdeModel {
    val tree = HollowIdeFileTree()
    val files = mutableStateMapOf<String, HollowIdeOpenFile>()
    val selectedTreePaths = mutableStateListOf<String>()
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
                selected = node.path in selectedTreePaths,
            )
        }
    }

    fun select(node: HollowIdeFileNode, additive: Boolean = false) {
        selectPath(node.path, additive)
    }

    fun focusSelection(node: HollowIdeFileNode) {
        selectedTreePath = node.path
        if (node.path !in selectedTreePaths) {
            selectedTreePaths.clear()
            selectedTreePaths += node.path
        }
    }

    fun selectPath(path: String, additive: Boolean = false) {
        selectedTreePath = path
        if (additive) {
            if (path in selectedTreePaths) selectedTreePaths.remove(path) else selectedTreePaths += path
        } else {
            selectedTreePaths.clear()
            selectedTreePaths += path
        }
    }

    fun toggle(node: HollowIdeFileNode, additive: Boolean = false) {
        select(node, additive)
        tree.toggle(node.path)
    }

    fun open(node: HollowIdeFileNode, additive: Boolean = false): HollowIdeOpenResult {
        select(node, additive)
        if (node.isDirectory) {
            tree.toggle(node.path)
            return HollowIdeOpenResult.Directory
        }
        return openFile(node.path)
    }

    fun openFile(path: String): HollowIdeOpenResult {
        selectedTreePath = path
        val file = path.fromReadablePath()
        if (!file.isFile) return HollowIdeOpenResult.Unsupported
        if (path.isModelEditorFile()) {
            files[path]?.let { return HollowIdeOpenResult.File(it, created = false) }
            val opened = HollowIdeOpenFile(path, "", readOnly = true)
            files[path] = opened
            return HollowIdeOpenResult.File(opened, created = true)
        }
        if (file.isProbablyBinary()) return HollowIdeOpenResult.Unsupported
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

    fun selectedOr(path: String): List<String> {
        return if (path in selectedTreePaths) selectedTreePaths.toList() else listOf(path)
    }

    fun createFile(parentPath: String, name: String): HollowIdeFileOperationResult {
        val cleanName = name.trim().replace('\\', '/').trim('/')
        if (cleanName.isBlank()) return HollowIdeFileOperationResult.InvalidName
        val parent = targetDirectory(parentPath)
        val target = parent.resolve(cleanName).toPath().normalizeInsideRoot() ?: return HollowIdeFileOperationResult.InvalidName
        if (Files.exists(target)) return HollowIdeFileOperationResult.AlreadyExists
        Files.createDirectories(target.parent)
        Files.createFile(target)
        tree.refresh()
        selectPath(target.toReadablePathInsideRoot())
        return HollowIdeFileOperationResult.Success
    }

    fun createFolder(parentPath: String, name: String): HollowIdeFileOperationResult {
        val cleanName = name.trim().replace('\\', '/').trim('/')
        if (cleanName.isBlank()) return HollowIdeFileOperationResult.InvalidName
        val parent = targetDirectory(parentPath)
        val target = parent.resolve(cleanName).toPath().normalizeInsideRoot() ?: return HollowIdeFileOperationResult.InvalidName
        if (Files.exists(target)) return HollowIdeFileOperationResult.AlreadyExists
        Files.createDirectories(target)
        tree.refresh()
        selectPath(target.toReadablePathInsideRoot())
        return HollowIdeFileOperationResult.Success
    }

    fun rename(path: String, newName: String): HollowIdeFileOperationResult {
        val cleanName = newName.trim()
        if (cleanName.isBlank() || cleanName.any { it == '/' || it == '\\' }) return HollowIdeFileOperationResult.InvalidName
        val source = path.toPathInsideRoot() ?: return HollowIdeFileOperationResult.NotFound
        if (!Files.exists(source)) return HollowIdeFileOperationResult.NotFound
        val target = source.parent.resolve(cleanName).normalizeInsideRoot() ?: return HollowIdeFileOperationResult.InvalidName
        if (Files.exists(target)) return HollowIdeFileOperationResult.AlreadyExists
        Files.move(source, target)
        val targetPath = target.toReadablePathInsideRoot()
        files.remove(path)?.let { opened ->
            files[targetPath] = opened.renamed(targetPath)
        }
        tree.refresh()
        selectPath(targetPath)
        return HollowIdeFileOperationResult.Success
    }

    fun delete(paths: List<String>): HollowIdeFileOperationResult {
        val targets = paths.mapNotNull { it.toPathInsideRoot() }.filter { Files.exists(it) }
            .filterNot { it == DirectoryManager.HOLLOW_ENGINE }
            .withoutNestedChildren()
        if (targets.isEmpty()) return HollowIdeFileOperationResult.NotFound
        targets.forEach { path -> path.toFile().deleteRecursively() }
        val removedReadable = targets.map { it.toReadablePathInsideRoot() }
        files.keys.filter { openPath -> removedReadable.any { openPath == it || openPath.startsWith("$it/") } }
            .forEach(files::remove)
        tree.refresh()
        selectedTreePaths.clear()
        selectedTreePath = ""
        return HollowIdeFileOperationResult.Success
    }

    fun copyToClipboard(paths: List<String>, cut: Boolean = false): HollowIdeFileOperationResult {
        val files = paths.mapNotNull { it.toPathInsideRoot()?.toFile() }.filter { it.exists() }
        if (files.isEmpty()) return HollowIdeFileOperationResult.NotFound
        HollowIdeFileClipboard.set(files, cut)
        return HollowIdeFileOperationResult.Success
    }

    fun pasteInto(targetPath: String): HollowIdeFileOperationResult {
        val targetDir = targetDirectory(targetPath).toPath().normalizeInsideRoot() ?: return HollowIdeFileOperationResult.InvalidName
        val clipboard = HollowIdeFileClipboard.get()
        if (clipboard.files.isEmpty()) return HollowIdeFileOperationResult.NotFound
        Files.createDirectories(targetDir)
        clipboard.files.forEach { sourceFile ->
            val source = sourceFile.toPath()
            if (!Files.exists(source)) return@forEach
            val destination = uniqueDestination(targetDir, source.fileName.toString())
            if (Files.isDirectory(source)) {
                source.copyDirectory(destination)
            } else {
                Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES)
            }
            if (clipboard.cut && source.normalizeInsideRoot() != null) {
                source.toFile().deleteRecursively()
            }
        }
        if (clipboard.cut) HollowIdeFileClipboard.clearInternalCut()
        tree.refresh()
        return HollowIdeFileOperationResult.Success
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

internal enum class HollowIdeFileOperationResult {
    Success,
    InvalidName,
    AlreadyExists,
    NotFound,
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

    fun renamed(nextPath: String): HollowIdeOpenFile {
        return HollowIdeOpenFile(nextPath, text, readOnly).also { renamed ->
            renamed.dirty = dirty
        }
    }
}

private data class HollowIdeClipboardPayload(
    val files: List<File>,
    val cut: Boolean,
)

private object HollowIdeFileClipboard {
    private var internal: HollowIdeClipboardPayload? = null

    fun set(files: List<File>, cut: Boolean) {
        internal = HollowIdeClipboardPayload(files, cut)
        if (!cut) {
            runCatching {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(FileListTransferable(files), null)
            }
        }
    }

    fun get(): HollowIdeClipboardPayload {
        val systemFiles = runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (!clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) return@runCatching emptyList<File>()
            @Suppress("UNCHECKED_CAST")
            clipboard.getData(DataFlavor.javaFileListFlavor) as? List<File> ?: emptyList()
        }.getOrDefault(emptyList())
        if (systemFiles.isNotEmpty()) return HollowIdeClipboardPayload(systemFiles, cut = false)
        return internal ?: HollowIdeClipboardPayload(emptyList(), cut = false)
    }

    fun clearInternalCut() {
        if (internal?.cut == true) internal = null
    }
}

private class FileListTransferable(private val files: List<File>) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
        return files
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
        root.refresh(root.expandedPaths())
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

    fun refresh(expandedPaths: Set<String> = expandedPaths()) {
        if (!isDirectory) return
        val files = path.fromReadablePath().listFiles().orEmpty()
        children.clear()
        children += files
            .sortedWith(compareBy<File> { it.isFile }.thenBy { it.name.lowercase() })
            .map { file ->
                val childPath = if (path.isEmpty()) file.name else "$path/${file.name}"
                HollowIdeFileNode(file.name, childPath, depth + 1, file.isDirectory).apply {
                    expanded = childPath in expandedPaths
                    if (expanded) refresh(expandedPaths)
                }
            }
    }

    fun expandedPaths(): Set<String> = buildSet {
        if (expanded) add(path)
        children.forEach { addAll(it.expandedPaths()) }
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

private fun targetDirectory(path: String): File {
    val file = path.fromReadablePath()
    return if (file.isDirectory || path.isBlank()) file else file.parentFile
}

private fun String.toPathInsideRoot(): Path? {
    return fromReadablePath().toPath().normalizeInsideRoot()
}

private fun Path.normalizeInsideRoot(): Path? {
    val root = DirectoryManager.HOLLOW_ENGINE.toAbsolutePath().normalize()
    val normalized = toAbsolutePath().normalize()
    return normalized.takeIf { it.isSameOrNestedIn(root) }
}

private fun List<Path>.withoutNestedChildren(): List<Path> {
    val sorted = map { it.toAbsolutePath().normalize() }.sortedBy { it.nameCount }
    val result = mutableListOf<Path>()
    for (path in sorted) {
        if (result.none { parent -> path != parent && path.isSameOrNestedIn(parent) }) result.add(path)
    }
    return result
}

private fun Path.isSameOrNestedIn(parent: Path): Boolean {
    val value = toComparablePathString()
    val root = parent.toComparablePathString().trimEnd('/')
    return value == root || value.startsWith("$root/")
}

private fun Path.toComparablePathString(): String {
    return toAbsolutePath().normalize().toString().replace('\\', '/')
}

private fun Path.toReadablePathInsideRoot(): String {
    val root = DirectoryManager.HOLLOW_ENGINE.toAbsolutePath().normalize()
    val normalized = toAbsolutePath().normalize()
    return root.relativize(normalized).toString().replace('\\', '/')
}

private fun uniqueDestination(targetDir: Path, fileName: String): Path {
    var candidate = targetDir.resolve(fileName)
    if (!Files.exists(candidate)) return candidate
    val extensionStart = fileName.lastIndexOf('.').takeIf { it > 0 }
    val baseName = extensionStart?.let { fileName.substring(0, it) } ?: fileName
    val extension = extensionStart?.let { fileName.substring(it) }.orEmpty()
    var index = 1
    while (Files.exists(candidate)) {
        candidate = targetDir.resolve("$baseName copy${if (index == 1) "" else " $index"}$extension")
        index++
    }
    return candidate
}

private fun Path.copyDirectory(target: Path) {
    Files.walk(this).use { stream ->
        stream.forEach { source ->
            val destination = target.resolve(relativize(source).toString())
            if (Files.isDirectory(source)) {
                Files.createDirectories(destination)
            } else {
                Files.createDirectories(destination.parent)
                Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES)
            }
        }
    }
}
