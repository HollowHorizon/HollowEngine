package ru.hollowhorizon.hollowengine.common.geary

import co.touchlab.kermit.Logger
import com.mineinabyss.geary.modules.UninitializedGearyModule
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.common.geary.config.Config
import ru.hollowhorizon.hollowengine.common.geary.di.DI

val gearyMinecraft: GearyMinecraftModule by DI.observe()

interface GearyMinecraftModule {
    val server: MinecraftServer
    val configHolder: Config<GearyMinecraftConfig>
    val config: GearyMinecraftConfig
    val logger: Logger
    val features: Features
    val gearyModule: UninitializedGearyModule
    val worldManager: WorldManager
}