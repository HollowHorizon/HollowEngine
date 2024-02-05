package ru.hollowhorizon.hollowengine.common.compact

import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.runtime.IJeiRuntime
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.HollowEngine.Companion.MODID

@JeiPlugin
class HollowJEIPlugin: IModPlugin {
    companion object {
        lateinit var hollowJeiRuntime: IJeiRuntime
    }

    override fun getPluginUid(): ResourceLocation = "${MODID}:jei".rl

    override fun onRuntimeAvailable(jeiRuntime: IJeiRuntime) {
        hollowJeiRuntime = jeiRuntime
    }
}