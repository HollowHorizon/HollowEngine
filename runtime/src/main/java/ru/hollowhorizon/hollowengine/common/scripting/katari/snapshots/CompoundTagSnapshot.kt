package ru.hollowhorizon.hollowengine.common.scripting.katari.snapshots

import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshot
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshotFactory
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptType
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForCompoundNBT

@Serializable
@SerialName("hollowengine:katari/compound_tag")
@ScriptType("CompoundTag")
data class CompoundTagSnapshot(
    val tag: @Serializable(ForCompoundNBT::class) CompoundTag,
) : ValueSnapshot(), ScriptSnapshot<CompoundTag> {
    override suspend fun restore(context: ValueRestoreContext): CompoundTag {
        return tag.copy()
    }

    companion object : ScriptSnapshotFactory<CompoundTag, CompoundTagSnapshot> {
        override fun capture(value: CompoundTag): CompoundTagSnapshot {
            return CompoundTagSnapshot(value.copy())
        }
    }
}
