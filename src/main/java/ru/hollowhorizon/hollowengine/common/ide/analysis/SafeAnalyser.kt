package ru.hollowhorizon.hollowengine.common.ide.analysis

import ru.hollowhorizon.hollowengine.HollowEngine

inline fun <R> runSafely(defaultValue: R? = null, action: () -> R): R? {
    return runCatching { action() }
        .onFailure { HollowEngine.LOGGER.error("Compiler error: ", it) }
        .getOrElse { defaultValue }
}