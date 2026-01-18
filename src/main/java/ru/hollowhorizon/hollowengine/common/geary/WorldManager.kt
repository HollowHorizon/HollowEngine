package ru.hollowhorizon.hollowengine.common.geary

import com.mineinabyss.geary.modules.Geary
import net.minecraft.world.level.Level

class WorldManager {
    private var _globalEngine: Geary? = null

    // В будущем можно сделать Map<Level, Geary>
    fun getGearyWorld(level: Level): Geary? = _globalEngine

    fun setGlobalEngine(engine: Geary) {
        _globalEngine = engine
    }

    val global get() = _globalEngine ?: error("No global Geary engine set")
}

fun Level.toGeary() = gearyMinecraft.worldManager.getGearyWorld(this)
    ?: error("No Geary engine found for level ${dimension().location()}")
