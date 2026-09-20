package ru.hollowhorizon.hollowengine.client.ui.notification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.HollowEngine

enum class NotificationKind {
    SUCCESS, INFO, WARNING, ERROR, PROGRESS;

    internal val icon: String
        get() = when (this) {
            SUCCESS -> "hollowengine:textures/gui/icons/success.svg"
            INFO -> "hollowengine:textures/gui/icons/launched.svg"
            WARNING -> "hollowengine:textures/gui/icons/changed.svg"
            ERROR -> "hollowengine:textures/gui/icons/failure.svg"
            PROGRESS -> "hollowengine:textures/gui/icons/run.svg"
        }

    internal val tag: String get() = name.lowercase()
}

/**
 * A button on a notification.
 */
class NotificationAction(
    val label: String,
    val dismisses: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * One entry in the stack. Everything but [id] can be replaced later through
 * [HollowNotifications.update], which is what lets a long job report its progress in place.
 */
class Notification internal constructor(
    val id: String,
    val kind: NotificationKind,
    val title: String,
    val icon: String,
    val actions: List<NotificationAction>,
    /** `0..1` while a job runs, `-1` for a bar that only shows the job has not finished. */
    val progress: Float?,
    val closable: Boolean,
    internal val expiresAt: Long?,
)

/**
 * The stack of notifications in the bottom-right corner.
 */
object HollowNotifications {
    /** How long a notification with nothing to click stays up. */
    private const val DefaultTimeoutMs = 6_000L

    /** Beyond this the oldest are dropped. */
    private const val MaxVisible = 5

    var entries by mutableStateOf<List<Notification>>(emptyList())
        private set

    private var counter = 0

    fun success(title: String, timeoutMs: Long? = DefaultTimeoutMs, action: NotificationAction? = null): String =
        show(NotificationKind.SUCCESS, title, timeoutMs, listOfNotNull(action))

    fun info(title: String, timeoutMs: Long? = DefaultTimeoutMs, action: NotificationAction? = null): String =
        show(NotificationKind.INFO, title, timeoutMs, listOfNotNull(action))

    fun warning(title: String, timeoutMs: Long? = DefaultTimeoutMs, action: NotificationAction? = null): String =
        show(NotificationKind.WARNING, title, timeoutMs, listOfNotNull(action))

    fun error(title: String, action: NotificationAction? = null): String =
        show(NotificationKind.ERROR, title, timeout = null, actions = listOfNotNull(action))

    fun undo(title: String, label: String, seconds: Int = 5, onUndo: () -> Unit): String = show(
        kind = NotificationKind.WARNING,
        title = title,
        timeout = seconds * 1000L,
        actions = listOf(NotificationAction(label, onClick = onUndo)),
    )

    fun progress(title: String, progress: Float = -1f, id: String = nextId()): String = show(
        kind = NotificationKind.PROGRESS,
        title = title,
        timeout = null,
        actions = emptyList(),
        progress = progress,
        id = id,
    )

    /** Replaces what a notification says without moving it in the stack. */
    fun update(id: String, title: String? = null, progress: Float? = null) = onRenderThread {
        entries = entries.map { entry ->
            if (entry.id != id) entry
            else Notification(
                id = entry.id,
                kind = entry.kind,
                title = title ?: entry.title,
                icon = entry.icon,
                actions = entry.actions,
                progress = progress ?: entry.progress,
                closable = entry.closable,
                expiresAt = entry.expiresAt,
            )
        }
    }

    fun finish(id: String, title: String, kind: NotificationKind = NotificationKind.SUCCESS) = onRenderThread {
        val existing = entries.firstOrNull { it.id == id }
        if (existing == null) {
            show(kind, title, DefaultTimeoutMs, emptyList())
            return@onRenderThread
        }
        entries = entries.map { entry ->
            if (entry.id != id) entry
            else Notification(
                id = id,
                kind = kind,
                title = title,
                icon = kind.icon,
                actions = emptyList(),
                progress = null,
                closable = true,
                expiresAt = System.currentTimeMillis() + DefaultTimeoutMs,
            )
        }
    }

    fun dismiss(id: String) = onRenderThread {
        entries = entries.filterNot { it.id == id }
    }

    fun clear() = onRenderThread { entries = emptyList() }

    internal fun advance(now: Long) {
        val current = entries
        if (current.none { it.expiresAt != null && it.expiresAt <= now }) return
        entries = current.filterNot { it.expiresAt != null && it.expiresAt <= now }
    }

    internal fun run(notification: Notification, action: NotificationAction) {
        if (action.dismisses) dismiss(notification.id)
        runCatching(action.onClick).onFailure {
            HollowEngine.LOGGER.warn("Notification action '{}' failed: {}", action.label, it.message)
        }
    }

    private fun show(
        kind: NotificationKind,
        title: String,
        timeout: Long?,
        actions: List<NotificationAction>,
        progress: Float? = null,
        id: String = nextId(),
    ): String {
        onRenderThread {
            val notification = Notification(
                id = id,
                kind = kind,
                title = title,
                icon = kind.icon,
                actions = actions,
                progress = progress,
                closable = actions.none { it.dismisses },
                expiresAt = timeout?.let { System.currentTimeMillis() + it },
            )
            entries = (entries.filterNot { it.id == id } + notification).takeLast(MaxVisible)
        }
        return id
    }

    private fun nextId(): String = "notification-${++counter}"

    private fun onRenderThread(block: () -> Unit) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.isSameThread) block() else minecraft.execute(block)
    }
}
