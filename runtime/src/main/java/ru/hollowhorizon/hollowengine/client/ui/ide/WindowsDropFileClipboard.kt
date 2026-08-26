package ru.hollowhorizon.hollowengine.client.ui.ide

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD.SIZE_T
import com.sun.jna.platform.win32.User32
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import ru.hollowhorizon.hollowengine.HollowEngine
import java.io.File

private const val FileDropFormat = 15
private const val AllDroppedFiles = -1
private const val DropEffectMove = 2
private const val MaxDroppedFileCount = 100_000
private const val MaxWindowsPathCharacters = 32_767
private const val PreferredDropEffect = "Preferred DropEffect"

internal data class WindowsDropFilePayload(
    val files: List<File>,
    val cut: Boolean,
)

/** Reads disk-backed files advertised through the native Windows CF_HDROP format. */
internal object WindowsDropFileClipboard {
    private val preferredDropEffectFormat by lazy {
        User32.INSTANCE.RegisterClipboardFormat(PreferredDropEffect)
    }

    fun read(): WindowsDropFilePayload? {
        if (!Platform.isWindows()) return null
        return runCatching { readNativeClipboard() }.onFailure { error ->
            HollowEngine.LOGGER.warn("Could not read files from the native Windows clipboard", error)
        }.getOrNull()
    }

    private fun readNativeClipboard(): WindowsDropFilePayload? {
        if (!NativeApis.user32.OpenClipboard(null)) return null
        return try {
            val files = readDroppedFiles()
            files.takeIf { it.isNotEmpty() }?.let {
                WindowsDropFilePayload(it, readsAsMove())
            }
        } finally {
            NativeApis.user32.CloseClipboard()
        }
    }

    private fun readDroppedFiles(): List<File> {
        if (!NativeApis.user32.IsClipboardFormatAvailable(FileDropFormat)) return emptyList()
        val dropHandle = NativeApis.user32.GetClipboardData(FileDropFormat) ?: return emptyList()
        val count = NativeApis.shell32.DragQueryFileW(dropHandle, AllDroppedFiles, null, 0)
        return collectWindowsDropFiles(
            count = count,
            pathLength = { index -> NativeApis.shell32.DragQueryFileW(dropHandle, index, null, 0) },
            readPath = { index, path ->
                NativeApis.shell32.DragQueryFileW(dropHandle, index, path, path.size)
            },
        )
    }

    private fun readsAsMove(): Boolean {
        val format = preferredDropEffectFormat
        if (format == 0 || !NativeApis.user32.IsClipboardFormatAvailable(format)) return false
        val handle = NativeApis.user32.GetClipboardData(format) ?: return false
        if (NativeApis.kernel32.GlobalSize(handle).toLong() < Int.SIZE_BYTES) return false
        val memory = NativeApis.kernel32.GlobalLock(handle) ?: return false
        return try {
            memory.getInt(0) and DropEffectMove != 0
        } finally {
            NativeApis.kernel32.GlobalUnlock(handle)
        }
    }

    private object NativeApis {
        val user32: DropFileUser32 = Native.load(
            "User32",
            DropFileUser32::class.java,
            W32APIOptions.DEFAULT_OPTIONS,
        )
        val shell32: FileDropShell32 = Native.load(
            "Shell32",
            FileDropShell32::class.java,
            W32APIOptions.UNICODE_OPTIONS,
        )
        val kernel32: DropFileKernel32 = Native.load(
            "Kernel32",
            DropFileKernel32::class.java,
            W32APIOptions.DEFAULT_OPTIONS,
        )
    }

    private interface DropFileUser32 : StdCallLibrary {
        fun OpenClipboard(owner: Pointer?): Boolean

        fun CloseClipboard(): Boolean

        fun IsClipboardFormatAvailable(format: Int): Boolean

        fun GetClipboardData(format: Int): Pointer?
    }

    private interface FileDropShell32 : StdCallLibrary {
        fun DragQueryFileW(dropHandle: Pointer, index: Int, path: CharArray?, pathLength: Int): Int
    }

    private interface DropFileKernel32 : StdCallLibrary {
        fun GlobalLock(memory: Pointer): Pointer?

        fun GlobalUnlock(memory: Pointer): Boolean

        fun GlobalSize(memory: Pointer): SIZE_T
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
