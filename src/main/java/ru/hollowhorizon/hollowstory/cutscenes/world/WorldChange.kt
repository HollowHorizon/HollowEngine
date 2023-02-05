@file:UseSerializers(ForBlockPos::class)

package ru.hollowhorizon.hollowstory.cutscenes.world

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.util.math.BlockPos
import ru.hollowhorizon.hc.client.utils.nbt.ForBlockPos

@Serializable
class WorldChange {
    var blockChanges = mutableMapOf<Int, BlockChange>()
    var timeChanges = mutableMapOf<Int, Long>()
    var weatherChanges = mutableMapOf<Int, WeatherChange>()

    @Serializable
    class BlockChange(val block: String, val pos: BlockPos, val state: ChangeType) {
        enum class ChangeType {
            ADD, REMOVE, USE
        }
    }

    @Serializable
    class WeatherChange(val type: WeatherType) {
        enum class WeatherType {
            CLEAR, RAIN, THUNDER
        }
    }
}
