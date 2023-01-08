package ru.hollowhorizon.hollowstory.script

import net.minecraftforge.common.MinecraftForge

class HollowScriptBase() {
    fun onStart() {
        MinecraftForge.EVENT_BUS.register(this)
    }

    fun onEnd() {
        MinecraftForge.EVENT_BUS.unregister(this)
    }
}