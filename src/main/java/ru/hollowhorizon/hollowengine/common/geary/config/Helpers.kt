package ru.hollowhorizon.hollowengine.common.geary.config

import kotlinx.serialization.serializer
import java.nio.file.Path

inline fun <reified T> config(
    name: String,
    path: Path,
    default: T,
    formats: ConfigFormats = ConfigFormats(),
    mergeUpdates: Boolean = true,
    preferredFormat: String = "yml",
    lazyLoad: Boolean = false,
    noinline onLoad: (T) -> Unit = {},
    noinline onFirstLoad: (T) -> Unit = {},
    noinline onReload: (T) -> Unit = {},
): Config<T> {
    return Config(
        name = name,
        path = path,
        default = default,
        serializer = serializer<T>(),
        preferredFormat = formats.extToFormat[preferredFormat]
            ?: error("Preferred format (with ext $preferredFormat) not found for config: $name"),
        formats = formats,
        mergeUpdates = mergeUpdates,
        lazyLoad = lazyLoad,
        onLoad = onLoad,
        onFirstLoad = onFirstLoad,
        onReload = onReload,
    )
}