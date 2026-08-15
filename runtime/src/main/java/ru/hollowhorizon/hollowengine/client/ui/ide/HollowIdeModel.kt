package ru.hollowhorizon.hollowengine.client.ui.ide

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
import ru.hollowhorizon.hollowengine.client.utils.IconHelper
import ru.hollowhorizon.hollowengine.client.ui.docking.DockItem
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTreeItem
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.SystemFlavorMap
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Base64

private const val AutoSaveDelayMillis = 900L

internal class HollowIdeModel(
    private val fileTypes: HollowIdeFileTypeRegistry,
) {
    val tree = HollowIdeFileTree()
    val files = mutableStateMapOf<String, HollowIdeOpenFile>()
    var onFileRemoved: ((String) -> Unit)? = null
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

    fun revealPath(path: String) {
        tree.expandTo(path)
        selectPath(path)
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
        if (file.isExtractedScript()) {
            val created = path !in files
            return HollowIdeOpenResult.File(openReadOnly(path, file.readText()), created)
        }
        files[path]?.let { opened ->
            if (!opened.dirty && opened.type.requiresContent) opened.refresh(file.readBytes())
            return HollowIdeOpenResult.File(opened, created = false)
        }
        val opened = runCatching {
            fileTypes.open(path, file::readBytes)?.also { opened ->
                opened.attachSaveHandler { save(path) }
            }
        }.getOrNull()
            ?: return HollowIdeOpenResult.Unsupported
        files[path] = opened
        return HollowIdeOpenResult.File(opened, created = true)
    }

    fun openReadOnly(path: String, text: String): HollowIdeOpenFile {
        files[path]?.let { opened ->
            if (opened.textOrNull != null) {
                opened.refreshReadOnly(text)
                return opened
            }
            files.remove(path)
            opened.close()
        }
        val textType = requireNotNull(fileTypes.find(BuiltinTextFileTypeId)) {
            "The built-in text file type is not registered"
        }
        return HollowIdeOpenFile(path, textType, HollowIdeTextDocument(text, readOnly = true)).also { opened ->
            opened.attachSaveHandler { save(path) }
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
        path.fromReadablePath().writeBytes(file.encode())
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

    /** Creates (if missing) and opens a starter `sounds.json` inside [dirPath], for the sounds editor. */
    fun createSoundsFile(dirPath: String): HollowIdeOpenResult {
        val directory = targetDirectory(dirPath)
        val target = File(directory, "sounds.json")
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            target.writeText("{\n}\n")
        }
        tree.refresh()
        val readablePath = if (dirPath.isBlank()) "sounds.json" else "$dirPath/sounds.json"
        selectPath(readablePath)
        return openFile(readablePath)
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
        val opened = files[path]
        if (opened?.dirty == true && !save(path)) return HollowIdeFileOperationResult.NotFound
        Files.move(source, target)
        val targetPath = target.toReadablePathInsideRoot()
        files.remove(path)
        onFileRemoved?.invoke(path)
        if (opened != null) {
            opened.close()
            runCatching {
                fileTypes.open(targetPath) { Files.readAllBytes(target) }?.also { reopened ->
                    reopened.attachSaveHandler { save(targetPath) }
                }
            }.getOrNull()?.let { files[targetPath] = it }
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
        targets.forEach { path -> path.deleteTree() }
        val removedReadable = targets.map { it.toReadablePathInsideRoot() }
        files.keys.filter { openPath -> removedReadable.any { openPath == it || openPath.startsWith("$it/") } }
            .toList()
            .forEach { path ->
                files.remove(path)?.close()
                onFileRemoved?.invoke(path)
            }
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

    /** Moves [paths] into the folder [targetPath] names (or the folder holding it, for a file). */
    fun moveInto(paths: List<String>, targetPath: String): HollowIdeFileOperationResult {
        val targetDir = targetDirectory(targetPath).toPath().normalizeInsideRoot()
            ?: return HollowIdeFileOperationResult.InvalidName
        val sources = paths.mapNotNull { it.toPathInsideRoot() }.filter { Files.exists(it) }
        if (sources.isEmpty()) return HollowIdeFileOperationResult.NotFound
        Files.createDirectories(targetDir)

        var moved = 0
        sources.forEach { source ->
            if (source.parent == targetDir || targetDir.swallowedBy(source)) return@forEach
            val destination = uniqueDestination(targetDir, source.fileName.toString())
            val openPath = source.toReadablePathInsideRoot()
            runCatching { Files.move(source, destination) }.onSuccess {
                moved++
                files.remove(openPath)?.close()
                onFileRemoved?.invoke(openPath)
            }
        }
        if (moved == 0) return HollowIdeFileOperationResult.InvalidName
        tree.refresh()
        selectPath(targetDir.toReadablePathInsideRoot())
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
            if (targetDir.swallowedBy(source)) return@forEach
            val destination = uniqueDestination(targetDir, source.fileName.toString())
            if (Files.isDirectory(source)) {
                source.copyDirectory(destination)
            } else {
                Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES)
            }
            if (clipboard.cut) runCatching { source.toFile().deleteRecursively() }
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
        val text = file.textOrNull ?: return
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

class HollowIdeOpenFile internal constructor(
    val path: String,
    val type: HollowIdeFileType,
    val document: HollowIdeFileDocument,
) {
    var dirty by mutableStateOf(false)
        private set
    private var saveHandler: (() -> Boolean)? = null

    val readOnly: Boolean get() = document.readOnly
    val text: String get() = requireNotNull(textOrNull) { "File '$path' is not a text document" }
    internal val textOrNull: String? get() = (document as? HollowIdeTextDocument)?.text
    val id: String = fileDockItemId(path)
    val title: String get() = path.substringAfterLast('/').ifEmpty { path }

    fun dockItem(): DockItem {
        return DockItem(id, title, IconHelper.forPath(path).toString(), dirty = dirty && !readOnly)
    }

    fun update(next: String) {
        val textDocument = document as? HollowIdeTextDocument ?: return
        if (textDocument.update(next)) dirty = true
    }

    fun markDirty() {
        if (!readOnly) dirty = true
    }

    fun updateDirty(modified: Boolean) {
        if (!readOnly) dirty = modified
    }

    fun save(): Boolean = saveHandler?.invoke() == true

    internal fun attachSaveHandler(handler: () -> Boolean) {
        saveHandler = handler
    }

    fun markSaved() {
        if (readOnly) return
        document.markSaved()
        dirty = false
    }

    fun markSavedIfText(savedText: String) {
        if (readOnly) return
        if (text == savedText) dirty = false
    }

    fun refresh(bytes: ByteArray) {
        if (dirty) return
        document.reload(bytes)
    }

    fun refreshReadOnly(next: String) {
        val textDocument = document as? HollowIdeTextDocument ?: return
        if (!readOnly) return
        textDocument.refresh(next)
        dirty = false
    }

    fun encode(): ByteArray = document.encode()

    fun close() = document.close()
}

private data class HollowIdeClipboardPayload(
    val files: List<File>,
    val cut: Boolean,
)

/**
 * The IDE's cut/copy buffer, which is the system one: files copied here paste into the file manager
 * and back. Whether a transfer was a cut travels in the `Preferred DropEffect` format Windows
 * Explorer uses; where that is unavailable a foreign transfer simply reads as a copy.
 */
private object HollowIdeFileClipboard {
    private const val DropEffectNative = "Preferred DropEffect"
    private const val DropEffectMove: Byte = 2
    private const val DropEffectCopy: Byte = 5

    private var internal: HollowIdeClipboardPayload? = null

    private val dropEffectFlavor: DataFlavor? = runCatching {
        val flavor = DataFlavor("application/x-java-serialized-object;class=java.io.InputStream", DropEffectNative)
        (SystemFlavorMap.getDefaultFlavorMap() as? SystemFlavorMap)?.apply {
            addUnencodedNativeForFlavor(flavor, DropEffectNative)
            addFlavorForUnencodedNative(DropEffectNative, flavor)
        }
        flavor
    }.getOrNull()

    fun set(files: List<File>, cut: Boolean) {
        internal = HollowIdeClipboardPayload(files, cut)
        val clipboard = runCatching { Toolkit.getDefaultToolkit().systemClipboard }.getOrNull() ?: return
        val cutEffect = dropEffectFlavor.takeIf { cut }
        runCatching { clipboard.setContents(FileListTransferable(files, cut, cutEffect), null) }
            .onFailure { runCatching { clipboard.setContents(FileListTransferable(files, cut, null), null) } }
    }

    fun get(): HollowIdeClipboardPayload {
        val clipboard = runCatching { Toolkit.getDefaultToolkit().systemClipboard }.getOrNull()
            ?: return internal ?: HollowIdeClipboardPayload(emptyList(), cut = false)
        val systemFiles = runCatching {
            if (!clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) return@runCatching emptyList<File>()
            @Suppress("UNCHECKED_CAST")
            clipboard.getData(DataFlavor.javaFileListFlavor) as? List<File> ?: emptyList()
        }.getOrDefault(emptyList())
        if (systemFiles.isEmpty()) return internal ?: HollowIdeClipboardPayload(emptyList(), cut = false)

        val own = internal?.takeIf { it.files.map(File::getAbsolutePath) == systemFiles.map(File::getAbsolutePath) }
        return HollowIdeClipboardPayload(systemFiles, cut = own?.cut ?: clipboard.readsAsMove())
    }

    fun clearInternalCut() {
        if (internal?.cut == true) internal = null
    }

    private fun Clipboard.readsAsMove(): Boolean {
        val flavor = dropEffectFlavor ?: return false
        return runCatching {
            if (!isDataFlavorAvailable(flavor)) return false
            (getData(flavor) as? InputStream)?.use { it.read() == DropEffectMove.toInt() } == true
        }.getOrDefault(false)
    }

    private class FileListTransferable(
        private val files: List<File>,
        private val cut: Boolean,
        private val dropEffectFlavor: DataFlavor?,
    ) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> =
            listOfNotNull(DataFlavor.javaFileListFlavor, dropEffectFlavor).toTypedArray()

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
            flavor == DataFlavor.javaFileListFlavor || flavor == dropEffectFlavor

        override fun getTransferData(flavor: DataFlavor): Any = when (flavor) {
            DataFlavor.javaFileListFlavor -> files
            dropEffectFlavor -> ByteArrayInputStream(
                byteArrayOf(if (cut) DropEffectMove else DropEffectCopy, 0, 0, 0),
            )

            else -> throw UnsupportedFlavorException(flavor)
        }
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

    /** Opens every folder on the way to [path] so the node is actually visible in the tree. */
    fun expandTo(path: String) {
        var node = root
        var prefix = ""
        for (segment in path.split('/')) {
            if (segment.isEmpty()) continue
            prefix = if (prefix.isEmpty()) segment else "$prefix/$segment"
            node = node.children.firstOrNull { it.path == prefix } ?: return
            if (node.isDirectory && !node.expanded) {
                node.expanded = true
                node.refresh()
            }
        }
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

private fun targetDirectory(path: String): File {
    val file = path.fromReadablePath()
    return if (file.isDirectory || path.isBlank()) file else file.parentFile
}

/** Whether a file is a copy the engine unpacked out of an addon rather than something authored here. */
private fun File.isExtractedScript(): Boolean {
    val path = toPath().toAbsolutePath().normalize()
    return sequenceOf(DirectoryManager.SCRIPT_SOURCE_CACHE, DirectoryManager.SCRIPT_BUNDLE_CACHE)
        .any { root -> path.startsWith(root.toPath().toAbsolutePath().normalize()) }
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

private fun Path.deleteTree() {
    if (!Files.exists(this, LinkOption.NOFOLLOW_LINKS)) return
    Files.walkFileTree(this, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            Files.deleteIfExists(file)
            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: Path, exception: IOException?): FileVisitResult {
            Files.deleteIfExists(dir)
            return FileVisitResult.CONTINUE
        }
    })
}

private fun Path.swallowedBy(source: Path): Boolean =
    toAbsolutePath().normalize().isSameOrNestedIn(source.toAbsolutePath().normalize())

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
