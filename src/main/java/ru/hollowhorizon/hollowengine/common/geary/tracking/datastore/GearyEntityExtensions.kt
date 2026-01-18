package ru.hollowhorizon.hollowengine.common.geary.tracking.datastore

import com.mineinabyss.geary.components.relations.InstanceOf
import com.mineinabyss.geary.datatypes.GearyEntity
import com.mineinabyss.geary.datatypes.GearyEntityType
import com.mineinabyss.geary.helpers.component
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.serialization.components.Persists
import com.mineinabyss.geary.serialization.getAllPersisting
import com.mineinabyss.geary.serialization.setAllPersisting
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.geary.gearyMinecraft
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity

context(world: Geary)
fun GearyEntity.encodeComponentsTo(tag: CompoundTag) = with(world) {
    val persisting = getAllPersisting()
    if (persisting.isEmpty() && getRelations<InstanceOf?, Any?>().isEmpty()) return
    persisting.forEach {
        getRelation<Persists>(component(it::class))?.hash = it.hashCode()
    }
    tag.encodeComponents(persisting, type)
}

fun GearyEntity.loadComponentsFrom(entity: MCEntity, tag: CompoundTag) {
    loadComponentsFrom(with(world) { tag.decodeComponents() })
    set(entity, entity::class)
    set(entity.uuid)
}

fun GearyEntity.loadComponentsFrom(decodedEntityData: DecodedEntityData) {
    val (components, type) = decodedEntityData
    setAllPersisting(components)
    with(world) {
        type.forEach { extend(it.toGeary()) }
    }
}

context(world: Geary)
fun GearyEntity.encodeComponentsTo(stack: ItemStack) {
    val tag = stack.orCreateTag
    encodeComponentsTo(tag)
}

context(world: Geary)
fun ItemStack.decodeComponents(): DecodedEntityData {
    val tag = this.tag ?: return DecodedEntityData(emptySet(), GearyEntityType())
    return with(gearyMinecraft) { tag.decodeComponents() }
}