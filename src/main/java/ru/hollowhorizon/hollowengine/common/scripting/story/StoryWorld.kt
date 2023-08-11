package ru.hollowhorizon.hollowengine.common.scripting.story

import net.minecraft.core.BlockPos
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import ru.hollowhorizon.hc.client.utils.rl

class StoryWorld(val level: ServerLevel) {
    fun placeBlock(block: String, pos: BlockPos, facing: FacingType, nbt: Tag? = null, playSound: Boolean = true) {

    }

    fun destroyBlock(pos: BlockPos, playSound: Boolean) {

    }

    fun useBlock(pos: BlockPos, item: String) {

    }

    fun getOrSpawnStructure(name: String) {
        TODO("Make structure system")
    }

    fun placeStructure(name: String, pos: BlockPos, facing: FacingType, animation: StructureAnimation) {
        //TODO: add structure animations

        val rotation = when (facing) {
            FacingType.NORTH -> Rotation.NONE
            FacingType.WEST -> Rotation.CLOCKWISE_90
            FacingType.SOUTH -> Rotation.CLOCKWISE_180
            FacingType.EAST -> Rotation.COUNTERCLOCKWISE_90
            else -> Rotation.NONE
        }

        this.level.structureManager.get(name.rl).orElseThrow()
            .placeInWorld(level, pos, pos, StructurePlaceSettings().setRotation(rotation), level.random, 3)
    }

    fun changeWeather(weather: WeatherType) {
        when (weather) {
            WeatherType.CLEAR -> this.level.setWeatherParameters(6000, 0, false, false)
            WeatherType.RAIN -> this.level.setWeatherParameters(0, 6000, true, false)
            WeatherType.THUNDER -> this.level.setWeatherParameters(0, 6000, true, true)
        }
    }

    fun setTime(time: Long) {
        this.level.dayTime = time
    }
}

//готовые пресеты, чтобы можно было прописать условно: world.setTime(DAY)
val SUNSET: Long
    get() = 0
val DAY: Long
    get() = 1000
val NOON: Long
    get() = 6000
val NIGHT: Long
    get() = 13000
val MIDNIGHT: Long
    get() = 18000

enum class StructureAnimation {
    NONE, DELAY, FROM_GROUND, FROM_CEILING
}

enum class FacingType {
    NORTH, SOUTH, WEST, EAST, UP, DOWN
}

enum class WeatherType {
    CLEAR, RAIN, THUNDER
}