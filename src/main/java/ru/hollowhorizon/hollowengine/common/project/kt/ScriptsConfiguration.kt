package ru.hollowhorizon.hollowengine.common.project.kt

public data class ScriptsConfiguration(
    /** Whether .kts scripts are handled. */
    var enabled: Boolean = true,
    /** Whether .gradle.kts scripts are handled. Only considered if scripts are enabled in general. */
    var buildScriptsEnabled: Boolean = false
)