package ru.hollowhorizon.hollowengine.common.geary

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import com.mineinabyss.geary.actions.GearyActions
import com.mineinabyss.geary.engine.Engine
import com.mineinabyss.geary.engine.archetypes.ArchetypeEngine
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.modules.GearyModule
import com.mineinabyss.geary.modules.geary
import com.mineinabyss.geary.serialization.dsl.withCommonComponentNames
import com.mineinabyss.geary.serialization.formats.YamlFormat
import com.mineinabyss.geary.serialization.serialization
import kotlinx.serialization.builtins.serializer
import net.minecraft.world.level.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.MarkerManager
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import ru.hollowhorizon.hollowengine.common.geary.tracking.EntityTracking
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.GearyNBTFormat
import org.apache.logging.log4j.Level as LogLevel

object GearyPlatform {
    val LOGGER = LogManager.getLogger("Geary")

    @JvmStatic
    fun create(level: Level): Geary = geary(createEngineModule(level)) {
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
    }.start()

    private fun createEngineModule(level: Level): GearyModule {
        val engine = com.mineinabyss.geary.modules.ArchetypeEngineModule(useSynchronized = true)

        return GearyModule(
            module {
                single { level }
                single {
                    MinecraftEngine(createLogger(), get())
                } withOptions {
                    bind<Engine>()
                    bind<ArchetypeEngine>()
                }
                includes(engine.module)
            },
            engine.properties
        )
    }

    private fun createLogger() = Logger(loggerConfigInit(object : LogWriter() {
        override fun log(
            severity: Severity,
            message: String,
            tag: String,
            throwable: Throwable?,
        ) {
            LOGGER.log(
                when (severity) {
                    Severity.Verbose -> LogLevel.TRACE
                    Severity.Debug -> LogLevel.DEBUG
                    Severity.Info -> LogLevel.INFO
                    Severity.Warn -> LogLevel.WARN
                    Severity.Error -> LogLevel.ERROR
                    Severity.Assert -> LogLevel.FATAL
                },
                MarkerManager.Log4jMarker(tag),
                message,
                throwable
            )
        }

    }))
}