package ru.hollowhorizon.hollowengine.common.addons

import java.io.File

data class HollowAddonContext(
    val metadata: HollowAddonMetadata,
    val addonFile: File,
    val classLoader: ClassLoader,
)
