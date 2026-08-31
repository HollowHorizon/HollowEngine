package ru.hollowhorizon.hollowengine.client.ui.ide

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD.SIZE_T
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/**
 * The Win32 entry points file transfer needs and jna-platform does not declare, one binding per
 * library so clipboard, drag source and drop target share a handle instead of loading their own.
 */
internal object WindowsApis {
    val user32: WindowsUser32 by lazy { load("User32", WindowsUser32::class.java) }
    val kernel32: WindowsKernel32 by lazy { load("Kernel32", WindowsKernel32::class.java) }
    val shell32: WindowsShell32 by lazy { load("Shell32", WindowsShell32::class.java) }
    val ole32: WindowsOle32 by lazy { load("Ole32", WindowsOle32::class.java) }

    private fun <T : StdCallLibrary> load(library: String, api: Class<T>): T =
        Native.load(library, api, W32APIOptions.DEFAULT_OPTIONS)
}

internal interface WindowsUser32 : StdCallLibrary {
    fun OpenClipboard(owner: Pointer?): Boolean

    fun CloseClipboard(): Boolean

    fun EmptyClipboard(): Boolean

    fun IsClipboardFormatAvailable(format: Int): Boolean

    fun GetClipboardData(format: Int): Pointer?

    fun SetClipboardData(format: Int, memory: Pointer): Pointer?

    fun ScreenToClient(window: HWND, point: POINT): Boolean

    fun ReleaseCapture(): Boolean

    fun GetAsyncKeyState(key: Int): Short

    fun GetWindowThreadProcessId(window: Pointer, processId: IntByReference?): Int
}

internal interface WindowsKernel32 : StdCallLibrary {
    fun GlobalAlloc(flags: Int, size: SIZE_T): Pointer?

    fun GlobalLock(memory: Pointer): Pointer?

    fun GlobalUnlock(memory: Pointer): Boolean

    fun GlobalSize(memory: Pointer): SIZE_T

    fun GlobalFree(memory: Pointer): Pointer?
}

internal interface WindowsShell32 : StdCallLibrary {
    fun DragQueryFileW(dropHandle: Pointer, index: Int, path: CharArray?, pathLength: Int): Int

    fun SHDoDragDrop(window: Pointer?, data: Pointer, source: Pointer?, allowed: Int, effect: IntByReference): Int

    fun SHCreateDataObject(
        folder: Pointer?, count: Int, children: Pointer?, inner: Pointer?, iid: GUID, result: PointerByReference,
    ): Int
}

internal interface WindowsOle32 : StdCallLibrary {
    fun OleGetClipboard(dataObject: PointerByReference): Int

    fun ReleaseStgMedium(medium: WindowsStorageMedium)

    fun RegisterDragDrop(window: HWND, target: Pointer): Int

    fun RevokeDragDrop(window: HWND): Int
}
