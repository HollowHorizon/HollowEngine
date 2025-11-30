package ru.hollowhorizon.hollowengine.common.utils

enum class Side {
    CLIENT, SERVER, BOTH;

    fun isClient() = this == CLIENT || this == BOTH
    fun isServer() = this == SERVER || this == BOTH

    inline fun runIf(isClient: Boolean, action: () -> Unit) {
        when (this) {
            CLIENT -> if (isClient) action()
            SERVER -> if (!isClient) action()
            BOTH -> action()
        }
    }
}