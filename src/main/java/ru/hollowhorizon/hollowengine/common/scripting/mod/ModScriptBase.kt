package ru.hollowhorizon.hollowengine.common.scripting.mod

import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.loading.FMLEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.story.ForgeEvent
import ru.hollowhorizon.hollowengine.common.scripting.story.IForgeEventScriptSupport

open class ModScriptBase : IForgeEventScriptSupport {
    override val forgeEvents = HashSet<ForgeEvent<*>>()
    val FORGE_BUS = MinecraftForge.EVENT_BUS
    val MOD_BUS = FMLJavaModLoadingContext.get().modEventBus
    val dist = FMLEnvironment.dist
}