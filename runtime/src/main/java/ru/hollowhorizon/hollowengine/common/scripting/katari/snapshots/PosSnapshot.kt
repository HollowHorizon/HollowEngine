package ru.hollowhorizon.hollowengine.common.scripting.katari.snapshots

import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshot
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshotFactory
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptType
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForVec3

@Serializable
@SerialName("hollowengine:katari/vec3")
@ScriptType("Vec3")
data class PosSnapshot(val pos: @Serializable(ForVec3::class) Vec3) : ValueSnapshot(), ScriptSnapshot<Vec3> {
    override suspend fun restore(context: ValueRestoreContext): Vec3 {
        return pos
    }

    companion object : ScriptSnapshotFactory<Vec3, PosSnapshot> {
        override fun capture(value: Vec3): PosSnapshot {
            return PosSnapshot(value)
        }
    }
}
