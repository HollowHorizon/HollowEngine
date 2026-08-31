package ru.hollowhorizon.hollowengine.client.ui.ide

import java.io.File

/** Window-thread lifecycle: queue from input callbacks, enter the native loop between frames. */
internal class NativeFileDragSession {
    private var pending: Request? = null
    private var running = false

    val busy: Boolean get() = pending != null || running

    fun request(files: List<File>, onFinished: () -> Unit): Boolean {
        if (files.isEmpty() || busy) return false
        pending = Request(files.toList(), onFinished)
        return true
    }

    fun runPending(perform: (List<File>) -> Unit) {
        val request = pending ?: return
        pending = null
        running = true
        try {
            perform(request.files)
        } finally {
            try {
                request.onFinished()
            } finally {
                running = false
            }
        }
    }

    private data class Request(val files: List<File>, val onFinished: () -> Unit)
}
