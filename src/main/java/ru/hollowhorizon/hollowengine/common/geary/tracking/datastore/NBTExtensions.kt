package ru.hollowhorizon.hollowengine.common.geary.tracking.datastore

import com.mineinabyss.geary.datatypes.GearyComponent
import com.mineinabyss.geary.datatypes.GearyEntityType
import com.mineinabyss.geary.modules.Geary
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization

context(world: Geary)
inline fun <reified T : GearyComponent> CompoundTag.has(): Boolean = false

context(world: Geary)
inline fun <reified T : GearyComponent> CompoundTag.remove() = Unit

context(world: Geary)
fun CompoundTag.encodeComponents(
    components: Collection<GearyComponent>,
    type: GearyEntityType,
) {
    put("snapshot", EntitySerialization.serializeToNbt(ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot(
        components = components.filterIsInstance<com.mineinabyss.geary.datatypes.Component>()
    )))
}

context(world: Geary)
fun CompoundTag.decodeComponents(): DecodedEntityData {
    val encoded = get("snapshot") ?: this
    val snapshot = EntitySerialization.deserializeFromNbt(encoded)
    return DecodedEntityData(
        persistingComponents = snapshot.components.filterIsInstance<GearyComponent>().toSet(),
        type = GearyEntityType(),
    )
}

fun CompoundTag.markComponentsEncoded() = Unit

val CompoundTag.hasComponentsEncoded: Boolean
    get() = contains("snapshot")
