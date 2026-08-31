package ru.hollowhorizon.hollowengine.client.ui.ide

import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import org.lwjgl.glfw.GLFWNativeWin32
import ru.hollowhorizon.hollowengine.HollowEngine
import java.io.File

/** OLE must run on the window thread whose input queue owns the mouse gesture. */
internal object WindowsFileDragSource {
    private val session = NativeFileDragSession()

    val active: Boolean get() = session.busy

    fun request(files: List<File>, onFinished: () -> Unit): Boolean =
        Platform.isWindows() && session.request(files, onFinished)

    /** Called outside GLFW callbacks: focus callbacks can themselves run inside SendMessage. */
    fun runPending(window: Long) {
        if (!session.busy) return
        try {
            session.runPending { files ->
                val owner = Pointer(GLFWNativeWin32.glfwGetWin32Window(window))
                check(WindowsApis.user32.GetWindowThreadProcessId(owner, null) == Kernel32.INSTANCE.GetCurrentThreadId()) {
                    "Native file drags must run on the window thread"
                }
                if (!leftButtonDown()) return@runPending
                val initialized = Ole32.INSTANCE.OleInitialize(Pointer.NULL).toInt()
                check(initialized >= 0) { "OleInitialize failed: ${initialized.toUInt().toString(16)}" }
                try {
                    createWindowsFileDataObject(files).use { data ->
                        if (!leftButtonDown()) return@runPending
                        WindowsApis.user32.ReleaseCapture()
                        val result = WindowsApis.shell32.SHDoDragDrop(
                            owner, data.pointer, null, WindowsDropEffectCopy, IntByReference(),
                        )
                        check(result >= 0) { "SHDoDragDrop failed: ${result.toUInt().toString(16)}" }
                    }
                } finally {
                    Ole32.INSTANCE.OleUninitialize()
                }
            }
        } catch (error: Exception) {
            HollowEngine.LOGGER.warn("Could not drag files to another application", error)
        }
    }

    /** The gesture is only ours to hand over while the button that started it is still held. */
    private fun leftButtonDown(): Boolean = WindowsApis.user32.GetAsyncKeyState(LeftMouseButton) < 0
}

private const val LeftMouseButton = 0x01

/** Shell-provided IDataObject implements format enumeration and COM lifetime management for us. */
internal fun createWindowsFileDataObject(files: List<File>): WindowsFileDataObject {
    val result = PointerByReference()
    val iid = GUID("{0000010E-0000-0000-C000-000000000046}")
    check(WindowsApis.shell32.SHCreateDataObject(null, 0, null, null, iid, result) >= 0)
    val data = WindowsFileDataObject(checkNotNull(result.value))
    try {
        WindowsFileTransferMemory(encodeWindowsFileDrop(files)).use { memory ->
            data.setFileDrop(checkNotNull(memory.handle))
            memory.transferOwnership()
        }
        return data
    } catch (failure: Throwable) {
        data.close()
        throw failure
    }
}

internal class WindowsFileDataObject(pointer: Pointer) : Unknown(pointer), AutoCloseable {
    fun setFileDrop(handle: Pointer) {
        val format = WindowsClipboardFormat().apply {
            cfFormat = WindowsFileDropFormat.toShort()
            aspect = 1
            storage = StorageGlobal
            write()
        }
        val medium = WindowsStorageMedium().apply {
            tymed = StorageGlobal
            data = handle
            write()
        }
        check(_invokeNativeInt(7, arrayOf(pointer, format.pointer, medium.pointer, 1)) >= 0)
    }

    override fun close() {
        Release()
    }
}
