package ru.hollowhorizon.hollowengine.client.ui.ide

import ru.hollowhorizon.hollowengine.HollowEngine
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.SystemFlavorMap
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private data class HollowIdeClipboardPayload(
    val files: List<File>,
    val cut: Boolean,
)

/**
 * The IDE's cut/copy buffer. Real files use AWT's cross-platform file-list flavor. Windows
 * applications can also expose files that do not exist on disk yet; those are handled by
 * [WindowsVirtualFileClipboard] after the regular format has been tried.
 */
internal object HollowIdeFileClipboard : ClipboardOwner {
    private const val DropEffectNative = "Preferred DropEffect"
    private const val DropEffectMove: Byte = 2
    private const val DropEffectCopy: Byte = 5

    @Volatile
    private var internal: HollowIdeClipboardPayload? = null

    @Volatile
    private var ownedTransferable: Transferable? = null

    private val dropEffectFlavor: DataFlavor? = runCatching {
        val flavor = DataFlavor("application/x-java-serialized-object;class=java.io.InputStream", DropEffectNative)
        (SystemFlavorMap.getDefaultFlavorMap() as? SystemFlavorMap)?.apply {
            addUnencodedNativeForFlavor(flavor, DropEffectNative)
            addFlavorForUnencodedNative(DropEffectNative, flavor)
        }
        flavor
    }.getOrNull()

    fun set(files: List<File>, cut: Boolean) {
        val payload = HollowIdeClipboardPayload(files, cut)
        internal = payload
        val clipboard = systemClipboard() ?: return
        val cutEffect = dropEffectFlavor.takeIf { cut }
        val primary = FileListTransferable(files, cut, cutEffect)
        if (setSystemContents(clipboard, primary)) return

        val fallback = FileListTransferable(files, cut, null)
        setSystemContents(clipboard, fallback)
    }

    fun pasteInto(targetDir: Path): Boolean {
        val clipboard = systemClipboard()
        val systemFiles = clipboard?.readFiles().orEmpty()
        if (systemFiles.isNotEmpty()) {
            val own = internal?.takeIf { payload -> payload.sameFilesAs(systemFiles) }
            val payload = HollowIdeClipboardPayload(systemFiles, own?.cut ?: (clipboard?.readsAsMove() == true))
            if (pasteRealFiles(payload, targetDir)) return true
        }

        WindowsDropFileClipboard.read()?.let { payload ->
            if (pasteRealFiles(HollowIdeClipboardPayload(payload.files, payload.cut), targetDir)) return true
        }

        if (WindowsVirtualFileClipboard.pasteInto(targetDir)) return true
        return internal?.let { pasteRealFiles(it, targetDir) } == true
    }

    override fun lostOwnership(clipboard: Clipboard, contents: Transferable) {
        if (ownedTransferable !== contents) return
        ownedTransferable = null
        internal = null
    }

    private fun setSystemContents(clipboard: Clipboard, contents: Transferable): Boolean {
        return runCatching {
            clipboard.setContents(contents, this)
            ownedTransferable = contents
        }.isSuccess
    }

    private fun pasteRealFiles(payload: HollowIdeClipboardPayload, targetDir: Path): Boolean {
        var pasted = false
        payload.files.forEach { sourceFile ->
            val source = sourceFile.toPath()
            if (!Files.exists(source) || targetDir.swallowedBy(source)) return@forEach
            val destination = uniqueDestination(targetDir, source.fileName.toString())
            val copied = runCatching {
                if (Files.isDirectory(source)) {
                    source.copyDirectory(destination)
                } else {
                    Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES)
                }
            }.onFailure { error ->
                HollowEngine.LOGGER.warn("Could not paste clipboard file '{}' into '{}'", source, targetDir, error)
            }.isSuccess
            if (!copied) return@forEach

            pasted = true
            if (payload.cut) runCatching { sourceFile.deleteRecursively() }
        }
        if (payload.cut) internal = null
        return pasted
    }

    private fun systemClipboard(): Clipboard? =
        runCatching { Toolkit.getDefaultToolkit().systemClipboard }.getOrNull()

    private fun Clipboard.readFiles(): List<File> = runCatching {
        if (!isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) return@runCatching emptyList()
        (getData(DataFlavor.javaFileListFlavor) as? List<*>)?.filterIsInstance<File>().orEmpty()
    }.onFailure { error ->
        HollowEngine.LOGGER.warn("Could not read the AWT file list from the system clipboard", error)
    }.getOrDefault(emptyList())

    private fun Clipboard.readsAsMove(): Boolean {
        val flavor = dropEffectFlavor ?: return false
        return runCatching {
            if (!isDataFlavorAvailable(flavor)) return false
            (getData(flavor) as? InputStream)?.use { it.read() == DropEffectMove.toInt() } == true
        }.getOrDefault(false)
    }

    private fun HollowIdeClipboardPayload.sameFilesAs(other: List<File>): Boolean =
        files.map(File::getAbsolutePath) == other.map(File::getAbsolutePath)

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
