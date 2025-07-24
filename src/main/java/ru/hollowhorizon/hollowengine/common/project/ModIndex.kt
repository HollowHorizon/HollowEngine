package ru.hollowhorizon.hollowengine.common.project

import io.github.douira.glsl_transformer.ast.query.index.Index
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import ru.hollowhorizon.hc.common.utils.toml.TomlFormat

@Serializable
class ModIndex {
    @SerialName("mod_id")
    var modId: String = "modid"

    @SerialName("mod_name")
    var modName: String = "Mod Name"

    @SerialName("mod_version")
    var modVersion: String = "1.0.0"

    fun save(): ByteArray {
        return TomlFormat.encodeToString(this).toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun from(bytes: ByteArray) = try {
            TomlFormat.decodeFromString<ModIndex>(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            ModIndex()
        }

        fun create(modId: String, modName: String, modVersion: String): ModIndex {
            return ModIndex().apply {
                this.modId = modId
                this.modName = modName
                this.modVersion = modVersion
            }
        }
    }
}
