package ru.hollowhorizon.hollowengine.common.scripting.source

import java.io.File

/**
 * One namespace worth of scripts. The sandbox directory and every addon that ships scripts are the
 * same kind of thing here: a named container that can hand out sources, precompiled artifacts, the
 * classpath its scripts compile against, and the classloader they run in.
 */
interface ScriptSource {
    val namespace: String

    /** Parent classloader for compiled scripts of this namespace. */
    val classLoader: ClassLoader

    /** Classpath entries beyond the engine's own, e.g. the addon jar and its bundled libraries. */
    val classpath: List<File>

    /** Namespaces this one may reference from `@file:Import`. */
    val dependencies: List<String>

    /**
     * Identifies the content of this source as a whole. It participates in cache keys so that
     * replacing an addon jar invalidates everything compiled from it.
     */
    val fingerprint: String

    fun list(): List<ScriptId>

    fun read(id: ScriptId): ScriptArtifacts?
}

/**
 * What a source can provide for a single script. [sourceFile] is absent when an addon was built
 * without its sources, [precompiled] is absent for scripts that were never compiled ahead of time.
 */
data class ScriptArtifacts(
    val id: ScriptId,
    val sourceFile: File?,
    val precompiled: File?,
) {
    init {
        require(sourceFile != null || precompiled != null) {
            "Script $id has neither a source file nor a precompiled artifact"
        }
    }
}
