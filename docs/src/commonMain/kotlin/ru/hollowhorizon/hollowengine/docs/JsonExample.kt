package ru.hollowhorizon.hollowengine.docs

import de.fabmax.kool.Assets
import de.fabmax.kool.loadBlob
import de.fabmax.kool.util.decodeToString
import de.fabmax.kool.util.launchOnMainThread
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun main1() {
    launchOnMainThread {
        val data = Assets.loadBlob("jsons/file.json").getOrThrow().decodeToString()

        @Serializable
        class Example(val a: Int = 1, val b: String = "")

        val a: Example = Json.decodeFromString(data)
    }
}