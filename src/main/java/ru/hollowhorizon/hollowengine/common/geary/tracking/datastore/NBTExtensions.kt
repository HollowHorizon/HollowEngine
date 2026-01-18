package ru.hollowhorizon.hollowengine.common.geary.tracking.datastore

import com.mineinabyss.geary.components.relations.InstanceOf
import com.mineinabyss.geary.datatypes.GearyComponent
import com.mineinabyss.geary.datatypes.GearyEntityType
import com.mineinabyss.geary.datatypes.toRelation
import com.mineinabyss.geary.helpers.componentId
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.modules.relationOf
import com.mineinabyss.geary.prefabs.PrefabKey
import com.mineinabyss.geary.prefabs.entityOfOrNull
import com.mineinabyss.geary.serialization.SerializableComponents
import com.mineinabyss.geary.serialization.formats.Formats
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.SetSerializer
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation

@PublishedApi
internal val Formats.nbt: GearyNBTFormat get() = get("nbt") as GearyNBTFormat

@PublishedApi
internal val Geary.serializers get() = getAddon(SerializableComponents).serializers

@PublishedApi
internal val Geary.formats get() = getAddon(SerializableComponents).formats

/** Проверяет наличие компонента в NBT */
context(world: Geary)
inline fun <reified T : GearyComponent> CompoundTag.has(): Boolean {
    val key = world.serializers.getResourceLocationFor<T>() ?: return false
    return this.contains(key.toString())
}

context(world: Geary)
inline fun <reified T : GearyComponent> CompoundTag.remove() {
    val key = world.serializers.getResourceLocationFor<T>() ?: return
    this.remove(key.toString())
}

/**
 * Кодирует компонент в NBT.
 */
context(world: Geary)
fun <T : GearyComponent> CompoundTag.encode(
    value: T,
    serializer: SerializationStrategy<T> = ((world.serializers.getSerializerFor(value::class)
        ?: error("Serializer not registered for ${value::class.simpleName}")) as SerializationStrategy<T>),
    key: ResourceLocation = world.serializers.getSerialNameFor(value::class)?.toComponentKey()
        ?: error("SerialName not registered for ${value::class.simpleName}"),
) {
    markComponentsEncoded()
    put(key.toString(), world.formats.nbt.encode(serializer, value))
}

/**
 * Декодирует компонент из NBT.
 */
context(world: Geary)
inline fun <reified T : GearyComponent> CompoundTag.decode(): T? {
    return decode(
        serializer = world.serializers.getSerializerFor(T::class) ?: return null,
        key = world.serializers.getSerialNameFor(T::class)?.toComponentKey() ?: return null
    )
}

context(world: Geary)
inline fun <reified T : GearyComponent> CompoundTag.decode(
    key: ResourceLocation,
    serializer: DeserializationStrategy<T>? = world.serializers.getSerializerForResource(key, T::class),
): T? {
    serializer ?: return null
    val keyString = key.toString()
    if (!contains(keyString)) return null

    val encoded = get(keyString) ?: return null
    return runCatching { world.formats.nbt.decode(serializer, encoded) }
        .onFailure { world.logger.e("Error while loading $key: ", it) }
        .getOrNull()
}

/**
 * Кодирует список компонентов.
 */
context(world: Geary)
fun CompoundTag.encodeComponents(
    components: Collection<GearyComponent>,
    type: GearyEntityType,
) = with(world) {
    markComponentsEncoded()

    // Очистка старых компонентов (фильтруем ключи, похожие на компоненты)
    val keysToRemove = allKeys
        .filter { it.isComponentKey() && it != GearyDatastore.COMPONENTS_KEY.toString() }
        .toList()

    keysToRemove.forEach { remove(it) }

    for (value in components)
        encode(value)

    val prefabs = type.filter { it.toRelation()?.kind == componentId<InstanceOf>() }
    if (prefabs.size != 0)
        encodePrefabs(prefabs.map { it.toRelation()!!.target.toGeary().get<PrefabKey>() }.filterNotNull())
}

/**
 * Кодирует PrefabKey.
 */
context(world: Geary)
fun CompoundTag.encodePrefabs(keys: Collection<PrefabKey>) {
    markComponentsEncoded()
    encode(
        keys.toSet(),
        SetSerializer(PrefabKey.serializer()),
        GearyDatastore.PREFABS_KEY
    )
}

/**
 * Декодирует PrefabKey.
 */
context(world: Geary)
fun CompoundTag.decodePrefabs(): Set<PrefabKey> =
    decode(GearyDatastore.PREFABS_KEY, SetSerializer(PrefabKey.serializer()))
        ?.map { key ->
            val migrated = PrefabNamespaceMigrations.migrations.getOrDefault(key.namespace, key.namespace)
            PrefabKey.of(migrated, key.key)
        }
        ?.toSet()
        ?: emptySet()

/**
 * Декодирует все компоненты.
 */
context(world: Geary)
fun CompoundTag.decodeComponents(): DecodedEntityData = with(world) {
    DecodedEntityData(
        persistingComponents = allKeys
            .filter {
                it.startsWith(COMPONENT_PREFIX) || (it.contains(":") && it.split(":")[1].startsWith(
                    COMPONENT_PREFIX
                ))
            } // Проверка на namespace:component.xyz
            .mapNotNull { keyString ->
                val resourceLoc = ResourceLocation.tryParse(keyString) ?: return@mapNotNull null
                decode<GearyComponent>(resourceLoc, world.serializers.getSerializerForResource(resourceLoc))
            }
            .toSet(),
        type = GearyEntityType(decodePrefabs().mapNotNull {
            relationOf<InstanceOf?>(entityOfOrNull(it) ?: return@mapNotNull null).id
        })
    )
}

// Вспомогательные методы для флагов
fun CompoundTag.markComponentsEncoded() {
    if (!hasComponentsEncoded) {
        putByte(GearyDatastore.COMPONENTS_KEY.toString(), 1)
    }
}

val CompoundTag.hasComponentsEncoded: Boolean
    get() = contains(GearyDatastore.COMPONENTS_KEY.toString()) || contains(GearyDatastore.PREFABS_KEY.toString())

