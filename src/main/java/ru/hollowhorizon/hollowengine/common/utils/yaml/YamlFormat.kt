package ru.hollowhorizon.hollowengine.common.utils.yaml

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

object YamlFormat {
    val yaml = Yaml.default

    inline fun <reified T> encodeToString(value: T): String {
        return yaml.encodeToString(value)
    }

    inline fun <reified T> decodeFromString(string: String): T {
        return yaml.decodeFromString(string)
    }
}