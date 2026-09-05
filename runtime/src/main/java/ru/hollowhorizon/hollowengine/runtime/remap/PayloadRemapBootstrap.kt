package ru.hollowhorizon.hollowengine.runtime.remap

import java.io.File

/**
 * Entry point that bootstrap calls to rewrite the payload before loading it.
 */
object PayloadRemapBootstrap {
    @JvmStatic
    fun remap(payload: String, table: String, output: String) {
        PayloadRemapTable.read(File(table)).applyTo(File(payload), File(output))
    }
}
