package ru.hollowhorizon.hollowengine.common.geary

import co.touchlab.kermit.Severity
import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable

@Serializable
class GearyMinecraftConfig(
    @YamlComment("Convert entities to and from geary.")
    val trackEntities: Boolean = true,
    val items: ItemTrackingConfig = ItemTrackingConfig(),
    val trackBlocks: Boolean = true,
    val catch: Catching = Catching(),
    val removeVanillaMobTypes: Set<String> = emptySet(), // Используем ID (String) или EntityType<?>
    val logLevel: Severity = Severity.Info,
    val resourcePack: ResourcePack = ResourcePack(),
    val spawning: Boolean = true,
)

@Serializable
class Catching(
    @YamlComment("Throw error on async write.")
    val asyncWrite: CatchType = CatchType.ERROR,
)

enum class CatchType { ERROR, IGNORE, WARN }

@Serializable
data class ItemTrackingConfig(
    val enabled: Boolean = true,
    val migrateByCustomModelData: Boolean = false,
)

@Serializable
data class ResourcePack(
    val generate: Boolean = true,
    val outputPath: String = "resourcepack.zip",
    val includedPackPath: String = ""
)