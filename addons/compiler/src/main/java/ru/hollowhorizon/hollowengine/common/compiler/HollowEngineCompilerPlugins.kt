package ru.hollowhorizon.hollowengine.common.compiler

import androidx.compose.compiler.plugins.kotlin.ComposePluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationComponentRegistrar
import ru.hollowhorizon.hollowengine.common.plugin.HollowEngineCompilerPlugin

/**
 * Creates the complete compiler-plugin set used by both script compilation and IDE analysis.
 *
 * Registrars are created per compiler/analysis environment because compiler plugins may keep
 * configuration state and must not leak it between sessions.
 */
@OptIn(ExperimentalCompilerApi::class)
internal fun createHollowEngineCompilerPluginRegistrars(): List<CompilerPluginRegistrar> = listOf(
    HollowEngineCompilerPlugin(),
    ComposePluginRegistrar(),
    SerializationComponentRegistrar(),
)
