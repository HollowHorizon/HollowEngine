package ru.hollowhorizon.hollowengine.common.scripting.katari.snapshots

import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariRestoreContext
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshot
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshotFactory
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptType
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForVec3

@Serializable
@SerialName("hollowengine:katari/damage_source")
@ScriptType("DamageSource")
class DamageSourceSnapshot(
    val type: @Serializable(ForResourceLocation::class) ResourceLocation,
    val causingEntity: EntitySnapshot?,
    val directEntity: EntitySnapshot?,
    val position: @Serializable(ForVec3::class) Vec3?,
) : ValueSnapshot(), ScriptSnapshot<DamageSource> {
    override suspend fun restore(context: ValueRestoreContext): DamageSource {
        context as KatariRestoreContext

        val registry = context.server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
        val holder = registry.getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, type))
        return if (position != null && directEntity == null) {
            DamageSource(holder, position)
        } else {
            DamageSource(holder, directEntity?.restore(context), causingEntity?.restore(context))
        }
    }

    companion object : ScriptSnapshotFactory<DamageSource, DamageSourceSnapshot> {
        override fun capture(value: DamageSource): DamageSourceSnapshot {
            return DamageSourceSnapshot(
                value.typeHolder().unwrapKey().get().location(),
                value.entity?.let { EntitySnapshot.capture(it) },
                value.directEntity?.let { EntitySnapshot.capture(it) },
                value.sourcePosition
            )
        }
    }
}

@ScriptBinding("type")
val DamageSource.scriptType: String get() = typeHolder().unwrapKey().get().toString()

@ScriptBinding("directEntity")
val DamageSource.scriptDirectEntity: Entity? get() = directEntity

@ScriptBinding("causingEntity")
val DamageSource.scriptCausingEntity: Entity? get() = entity

@ScriptBinding("position")
val DamageSource.scriptPosition: Vec3? get() = sourcePosition

