package ru.hollowhorizon.hollowengine.client.ui.ide

import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import ru.hollowhorizon.hollowengine.HollowEngine
import java.io.File

private const val AllDroppedFiles = -1
private const val DropEffectMove = 2
private const val MaxDroppedFileCount = 100_000
private const val MaxWindowsPathCharacters = 32_767
private const val PreferredDropEffect = "Preferred DropEffect"

internal data class WindowsDropFilePayload(
    val files: List<File>,
    val cut: Boolean,
)

/** Exchanges disk-backed files through the native Windows CF_HDROP format, including in headless JVMs. */
internal object WindowsDropFileClipboard {
    private val preferredDropEffectFormat by lazy {
        User32.INSTANCE.RegisterClipboardFormat(PreferredDropEffect)
    }

    fun write(files: List<File>, cut: Boolean, owner: Pointer): Boolean {
        if (!Platform.isWindows() || files.isEmpty()) return false
        return runCatching {
            WindowsFileTransferMemory(encodeWindowsFileDrop(files)).use { paths ->
                val effect = if (cut) WindowsDropEffectMove else WindowsDropEffectCopy
                WindowsFileTransferMemory(byteArrayOf(effect.toByte(), 0, 0, 0)).use { preferredEffect ->
                    check(WindowsApis.user32.OpenClipboard(owner)) { "Windows clipboard is busy" }
                    try {
                        check(WindowsApis.user32.EmptyClipboard())
                        checkNotNull(WindowsApis.user32.SetClipboardData(WindowsFileDropFormat, paths.handle!!))
                        paths.transferOwnership()
                        val format = preferredDropEffectFormat
                        if (format != 0 && WindowsApis.user32.SetClipboardData(format, preferredEffect.handle!!) != null) {
                            preferredEffect.transferOwnership()
                        } else {
                            check(!cut) { "Could not publish the cut operation" }
                        }
                    } finally {
                        WindowsApis.user32.CloseClipboard()
                    }
                }
            }
        }.onFailure { error ->
            HollowEngine.LOGGER.warn("Could not write files to the native Windows clipboard", error)
        }.isSuccess
    }

    fun read(): WindowsDropFilePayload? {
        if (!Platform.isWindows()) return null
        return runCatching { readNativeClipboard() }.onFailure { error ->
            HollowEngine.LOGGER.warn("Could not read files from the native Windows clipboard", error)
        }.getOrNull()
    }

    private fun readNativeClipboard(): WindowsDropFilePayload? {
        if (!WindowsApis.user32.OpenClipboard(null)) return null
        return try {
            val files = readDroppedFiles()
            files.takeIf { it.isNotEmpty() }?.let {
                WindowsDropFilePayload(it, readsAsMove())
            }
        } finally {
            WindowsApis.user32.CloseClipboard()
        }
    }

    private fun readDroppedFiles(): List<File> {
        if (!WindowsApis.user32.IsClipboardFormatAvailable(WindowsFileDropFormat)) return emptyList()
        val dropHandle = WindowsApis.user32.GetClipboardData(WindowsFileDropFormat) ?: return emptyList()
        return readFiles(dropHandle)
    }

    internal fun readFiles(dropHandle: Pointer): List<File> {
        val count = WindowsApis.shell32.DragQueryFileW(dropHandle, AllDroppedFiles, null, 0)
        return collectWindowsDropFiles(
            count = count,
            pathLength = { index -> WindowsApis.shell32.DragQueryFileW(dropHandle, index, null, 0) },
            readPath = { index, path ->
                WindowsApis.shell32.DragQueryFileW(dropHandle, index, path, path.size)
            },
        )
    }

    private fun readsAsMove(): Boolean {
        val format = preferredDropEffectFormat
        if (format == 0 || !WindowsApis.user32.IsClipboardFormatAvailable(format)) return false
        val handle = WindowsApis.user32.GetClipboardData(format) ?: return false
        if (WindowsApis.kernel32.GlobalSize(handle).toLong() < Int.SIZE_BYTES) return false
        val memory = WindowsApis.kernel32.GlobalLock(handle) ?: return false
        return try {
            memory.getInt(0) and DropEffectMove != 0
        } finally {
            WindowsApis.kernel32.GlobalUnlock(handle)
        }
    }

}

internal fun collectWindowsDropFiles(
    count: Int,
    pathLength: (Int) -> Int,
    readPath: (Int, CharArray) -> Int,
): List<File> {
    if (count !in 1..MaxDroppedFileCount) return emptyList()
    return buildList(count) {
        repeat(count) { index ->
            val length = pathLength(index)
            if (length !in 1..MaxWindowsPathCharacters) return@repeat
            val path = CharArray(length + 1)
            val copied = readPath(index, path)
            if (copied !in 1..length) return@repeat
            add(File(String(path, 0, copied)))
        }
    }
}
