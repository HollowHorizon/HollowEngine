package ru.hollowhorizon.hollowengine.common.utils.yaml

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import ru.hollowhorizon.hollowengine.common.attachments.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.attachments.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.attachments.tracking.MCEntity

object YamlFormat {
    val yaml = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun withModule(serializersModule: SerializersModule): Json {
        return Json(yaml) {
            this.serializersModule = serializersModule
        }
    }

    inline fun <reified T> encodeToString(value: T): String {
        return yaml.encodeToString(value)
    }

    fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T, serializersModule: SerializersModule? = null): String {
        val y = if (serializersModule != null) withModule(serializersModule) else yaml
        return y.encodeToString(serializer, value)
    }

    inline fun <reified T> decodeFromString(string: String): T {
        return yaml.decodeFromString(string)
    }

    fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String, serializersModule: SerializersModule? = null): T {
        val y = if (serializersModule != null) withModule(serializersModule) else yaml
        return y.decodeFromString(deserializer, string)
    }

    fun serializeEntity(snapshot: EntitySnapshot): String = EntitySerialization.serializeToYaml(snapshot)
    fun serializeEntity(entity: MCEntity): String = EntitySerialization.serializeEntityToYaml(entity)
    fun deserializeEntity(string: String): EntitySnapshot = EntitySerialization.deserializeFromYaml(string)
    fun deserializeInto(target: MCEntity, string: String): EntitySnapshot = EntitySerialization.deserializeInto(target, string)
}
