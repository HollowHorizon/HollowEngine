package ru.hollowhorizon.hollowengine.client.ui

internal class TextFieldValueSync(initial: String) {
    var lastExternal: String = initial
        private set
    var lastNotified: String = initial
        private set
    private val pendingNotifications = ArrayDeque<String>()

    fun recordNotification(value: String) {
        lastNotified = value
        pendingNotifications.addLast(value)
        while (pendingNotifications.size > PendingNotificationLimit) pendingNotifications.removeFirst()
    }

    fun acknowledge(value: String): Boolean {
        val index = pendingNotifications.indexOf(value)
        if (index < 0) return false
        repeat(index + 1) { pendingNotifications.removeFirst() }
        return true
    }

    fun updateExternal(value: String) {
        lastExternal = value
    }

    fun reset(value: String) {
        lastNotified = value
        pendingNotifications.clear()
    }

    private companion object {
        const val PendingNotificationLimit = 32
    }
}
