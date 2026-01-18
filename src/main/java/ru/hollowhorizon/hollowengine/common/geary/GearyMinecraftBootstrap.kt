package ru.hollowhorizon.hollowengine.common.geary

import co.touchlab.kermit.Logger
import com.mineinabyss.geary.actions.GearyActions
import com.mineinabyss.geary.engine.Engine
import com.mineinabyss.geary.engine.archetypes.ArchetypeEngine
import com.mineinabyss.geary.helpers.async.IgnoringAsyncCatcher
import com.mineinabyss.geary.modules.GearyModule
import com.mineinabyss.geary.modules.geary
import com.mineinabyss.geary.serialization.dsl.withCommonComponentNames
import com.mineinabyss.geary.serialization.formats.YamlFormat
import com.mineinabyss.geary.serialization.serialization
import kotlinx.coroutines.CoroutineName
import kotlinx.serialization.builtins.serializer
import net.minecraft.server.MinecraftServer
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import ru.hollowhorizon.hollowengine.common.coroutines.dispatcher
import ru.hollowhorizon.hollowengine.common.geary.config.config
import ru.hollowhorizon.hollowengine.common.geary.di.DI
import ru.hollowhorizon.hollowengine.common.geary.tracking.EntityTracking
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.GearyNBTFormat
import java.nio.file.Path

class GearyMinecraftBootstrap(
    val server: MinecraftServer,
    val dataPath: Path
) {
    val features = Features(
        server,
        Logger.withTag("Geary")
    )

    fun onServerStarting() {
        val configHolder = config(
            "config", dataPath, GearyMinecraftConfig()
        )

        val module = object : GearyMinecraftModule {
            override val server = this@GearyMinecraftBootstrap.server
            override val configHolder = configHolder
            override val config by configHolder
            override val logger = Logger.withTag("MCGeary")
            override val features = this@GearyMinecraftBootstrap.features
            override val gearyModule = geary(createEngineModule(config, server))
            override val worldManager = WorldManager()
        }

        DI.add<GearyMinecraftModule>(module)

        module.gearyModule.configure {
            install(GearyActions)
            install(EntityTracking)

            serialization {
                components {
                    component(String.serializer())
                }
                format("yml", ::YamlFormat)
                format("nbt", ::GearyNBTFormat)
                withCommonComponentNames()

            }

        }

        features.loadAll()

        // Start Engine
        module.worldManager.setGlobalEngine(module.gearyModule.start())
        features.enableAll()
    }

    fun onServerStopping() {
        features.disableAll()
    }

    fun onTick() {
        (gearyMinecraft.worldManager.global.engine as? MinecraftEngine)?.tick()
    }

    private fun createEngineModule(config: GearyMinecraftConfig, server: MinecraftServer): GearyModule {
        val engine = com.mineinabyss.geary.modules.ArchetypeEngineModule(
            useSynchronized = true,
            engineThread = { server.dispatcher + CoroutineName("Geary Engine") },
            properties = mapOf(
                "asyncCatcher.write" to when (config.catch.asyncWrite) {
                    CatchType.IGNORE -> IgnoringAsyncCatcher()
                    CatchType.WARN -> MinecraftWarningAsyncCatcher(server)
                    CatchType.ERROR -> MinecraftAsyncCatcher(server)
                }
            )
        )

        return GearyModule(
            module {
                single { server }
                single {
                    MinecraftEngine(get(), get(), server) { server.dispatcher }
                } withOptions {
                    bind<Engine>()
                    bind<ArchetypeEngine>()
                }
                includes(engine.module)
            },
            engine.properties
        )
    }
}