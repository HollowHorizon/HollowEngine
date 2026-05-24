package ru.hollowhorizon.hollowengine.common.scripting.katari.snapshots

import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshot
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshotFactory
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptType
import ru.hollowhorizon.hollowengine.common.utils.rl

@Serializable
@SerialName("hollowengine:katari/block_pos")
@ScriptType("BlockPos")
data class BlockPosSnapshot(
    val x: Int,
    val y: Int,
    val z: Int,
) : ValueSnapshot(), ScriptSnapshot<BlockPos> {
    override suspend fun restore(context: ValueRestoreContext): BlockPos {
        return BlockPos(x, y, z)
    }

    companion object : ScriptSnapshotFactory<BlockPos, BlockPosSnapshot> {
        override fun capture(value: BlockPos): BlockPosSnapshot {
            return BlockPosSnapshot(value.x, value.y, value.z)
        }
    }
}

@Serializable
@SerialName("hollowengine:katari/block_state")
@ScriptType("BlockState")
data class BlockStateSnapshot(
    val block: String,
    val properties: Map<String, String>,
) : ValueSnapshot(), ScriptSnapshot<BlockState> {
    override suspend fun restore(context: ValueRestoreContext): BlockState {
        val blockState = BuiltInRegistries.BLOCK.get(block.rl)?.defaultBlockState()
            ?: Blocks.AIR.defaultBlockState()
        return properties.entries.fold(blockState) { state, (name, value) ->
            val property = state.properties.firstOrNull { it.name == name } ?: return@fold state
            state.setPropertyValue(property, value) ?: state
        }
    }

    companion object : ScriptSnapshotFactory<BlockState, BlockStateSnapshot> {
        override fun capture(value: BlockState): BlockStateSnapshot {
            val properties = value.values.entries.associate { (property, propertyValue) ->
                property.name to property.valueName(propertyValue)
            }
            return BlockStateSnapshot(BuiltInRegistries.BLOCK.getKey(value.block).toString(), properties)
        }
    }
}

private fun <T : Comparable<T>> BlockState.setPropertyValue(property: Property<T>, value: String): BlockState? {
    val restoredValue = property.getValue(value).orElse(null) ?: return null
    return setValue(property, restoredValue)
}

private fun <T : Comparable<T>> Property<T>.valueName(value: Comparable<*>): String {
    @Suppress("UNCHECKED_CAST")
    return getName(value as T)
}
