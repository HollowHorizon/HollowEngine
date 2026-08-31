package ru.hollowhorizon.hollowengine.client.ui.ide

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD.SIZE_T
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal const val WindowsFileDropFormat = 15
internal const val WindowsDropEffectCopy = 1
internal const val WindowsDropEffectMove = 2

/** CF_HDROP contains a DROPFILES header followed by a double-NUL-terminated UTF-16 path list. */
internal fun encodeWindowsFileDrop(files: List<File>): ByteArray {
    require(files.isNotEmpty())
    val paths = files.map { it.absoluteFile.normalize().path }
    require(paths.none { '\u0000' in it })
    val names = (paths.joinToString("\u0000") + "\u0000\u0000").toByteArray(Charsets.UTF_16LE)
    val headerSize = 20
    return ByteBuffer.allocate(headerSize + names.size).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(headerSize).putInt(0).putInt(0).putInt(0).putInt(1).put(names).array()
}

/** Owns an HGLOBAL until Windows accepts ownership through SetClipboardData / IDataObject.SetData. */
internal class WindowsFileTransferMemory(bytes: ByteArray) : AutoCloseable {
    var handle: Pointer? = checkNotNull(WindowsApis.kernel32.GlobalAlloc(0x0002, SIZE_T(bytes.size.toLong())))
        private set

    init {
        try {
            val memory = checkNotNull(WindowsApis.kernel32.GlobalLock(handle!!))
            try {
                memory.write(0, bytes, 0, bytes.size)
            } finally {
                WindowsApis.kernel32.GlobalUnlock(handle!!)
            }
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    fun transferOwnership() {
        handle = null
    }

    override fun close() {
        handle?.let { WindowsApis.kernel32.GlobalFree(it) }
        handle = null
    }
}
