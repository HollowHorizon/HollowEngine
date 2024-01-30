package ru.hollowhorizon.hollowengine.common.scripting.mod

import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.loading.FMLEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.story.ForgeEvent
import ru.hollowhorizon.hollowengine.common.scripting.story.IForgeEventScriptSupport

open class ModScriptBase : IForgeEventScriptSupport {
    override val forgeEvents = HashSet<ForgeEvent<*>>()
    val FORGE_BUS: IEventBus = MinecraftForge.EVENT_BUS
    val MOD_BUS: IEventBus = thedarkcolour.kotlinforforge.forge.MOD_BUS
    val dist = FMLEnvironment.dist
}