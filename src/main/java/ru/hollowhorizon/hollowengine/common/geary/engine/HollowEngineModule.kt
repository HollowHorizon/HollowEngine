package ru.hollowhorizon.hollowengine.common.geary.engine

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import com.mineinabyss.geary.datatypes.maps.ArrayTypeMap
import com.mineinabyss.geary.datatypes.maps.SynchronizedArrayTypeMap
import com.mineinabyss.geary.datatypes.maps.TypeMap
import com.mineinabyss.geary.engine.*
import com.mineinabyss.geary.engine.archetypes.*
import com.mineinabyss.geary.engine.archetypes.operations.ArchetypeMutateOperations
import com.mineinabyss.geary.engine.archetypes.operations.ArchetypeReadOperations
import com.mineinabyss.geary.helpers.async.AsyncCatcher
import com.mineinabyss.geary.helpers.async.IgnoringAsyncCatcher
import com.mineinabyss.geary.modules.*
import com.mineinabyss.geary.observers.ArchetypeEventRunner
import com.mineinabyss.geary.observers.EventRunner
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.MarkerManager
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import ru.hollowhorizon.hollowengine.common.geary.GearyPlatform.LOGGER
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal object ArchetypesModules {
    // Module for any classes without other dependencies as parameters
    val noDependencies get() = module {
        single<Logger> { createLogger() }
        single { if (getProperty("useSynchronized")) SynchronizedArrayTypeMap() else ArrayTypeMap() } withOptions {
            bind<TypeMap>()
        }
        single<AsyncCatcher> { IgnoringAsyncCatcher() }
    }

    val archetypes get() = module {
        includes(noDependencies)
        single { ArchetypeQueryManager() } withOptions { bind<QueryManager>() }
        singleOf(::SimpleArchetypeProvider) { bind<ArchetypeProvider>() }
    }

    val entities get() = module {
        includes(archetypes)
        single {
            EntityByArchetypeProvider(getProperty("reuseIDsAfterRemoval"), get(), get())
        } withOptions { bind<EntityProvider>() }
    }

    val components get() = module {
        includes(entities)
        singleOf(::HollowEngineComponentProvider) { bind<ComponentProvider>() }
        singleOf(::Components)
    }

    val core get() = module {
        includes(components)
        singleOf(::ArchetypeReadOperations) { bind<EntityReadOperations>() }
        singleOf(::PipelineImpl) { bind<Pipeline>() }
        singleOf(::EntityInfoReader)
    }

    val engine get() = module {
        includes(core)
        single {
            ArchetypeEngine(get(), get(), getProperty("tickDuration"), getProperty("engineThread"))
        } withOptions { bind<Engine>() }
    }
}

fun HollowEngineModule(
    tickDuration: Duration = 50.milliseconds,
    reuseIDsAfterRemoval: Boolean = true,
    useSynchronized: Boolean = false,
    beginTickingOnStart: Boolean = true,
    defaults: Defaults = Defaults(),
    engineThread: () -> CoroutineContext = { (CoroutineScope(Dispatchers.Default) + CoroutineName("Geary Engine")).coroutineContext },
    properties: Map<String, Any> = emptyMap()
) = GearyModule(
    module {
        includes(ArchetypesModules.engine)
        singleOf(::ArchetypeEventRunner) { bind<EventRunner>() }
        single {
            ArchetypeMutateOperations(
                get(),
                get(),
                get(),
                get(),
                get(),
                getPropertyOrNull("asyncCatcher.write") ?: get()
            )
        } withOptions { bind<EntityMutateOperations>() }
        singleOf(::EntityRemove)
        single {
            ArchetypeEngineInitializer(getProperty("beginTickingOnStart"), get(), get())
        } withOptions {
            bind<EngineInitializer>()
        }
        singleOf(::MutableAddons)
    }, properties = mapOf(
        "tickDuration" to tickDuration,
        "reuseIDsAfterRemoval" to reuseIDsAfterRemoval,
        "useSynchronized" to useSynchronized,
        "beginTickingOnStart" to beginTickingOnStart,
        "defaults" to defaults,
        "engineThread" to engineThread
    ) + properties
)

private fun createLogger() = Logger(loggerConfigInit(object : LogWriter() {
    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ) {
        LOGGER.log(
            when (severity) {
                Severity.Verbose -> Level.TRACE
                Severity.Debug -> Level.DEBUG
                Severity.Info -> Level.INFO
                Severity.Warn -> Level.WARN
                Severity.Error -> Level.ERROR
                Severity.Assert -> Level.FATAL
            },
            MarkerManager.Log4jMarker(tag),
            message,
            throwable
        )
    }

}))