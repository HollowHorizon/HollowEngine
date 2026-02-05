package ru.hollowhorizon.hollowengine.common.geary

import com.mineinabyss.geary.actions.GearyActions
import com.mineinabyss.geary.engine.Engine
import com.mineinabyss.geary.engine.archetypes.ArchetypeEngine
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.modules.GearyModule
import com.mineinabyss.geary.modules.geary
import com.mineinabyss.geary.serialization.dsl.withCommonComponentNames
import com.mineinabyss.geary.serialization.formats.YamlFormat
import com.mineinabyss.geary.serialization.serialization
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.level.Level
import org.apache.logging.log4j.LogManager
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.geary.engine.HollowEngineModule
import ru.hollowhorizon.hollowengine.common.geary.sync.SyncableComponents
import ru.hollowhorizon.hollowengine.common.geary.tracking.EntityTracking
import ru.hollowhorizon.hollowengine.common.geary.tracking.MinecraftEntityLookup
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.GearyNBTFormat
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks

@Serializable
@SerialName("hollowengine:list_of_string")
class ListString(val list: List<String>)

object GearyPlatform {
    val LOGGER = LogManager.getLogger("Geary")

    @JvmStatic
    fun create(level: Level): Geary = geary(createEngineModule(level)) {
        serialization {
            components {
                ComponentRegistry.map { it.value }.forEach {
                    component(it.value, JavaHacks.forceCast(it.serializer))
                }
            }
            format("yml", ::YamlFormat)
            format("nbt", ::GearyNBTFormat)
            withCommonComponentNames()

        }

        install(GearyActions)
        install(EntityTracking)
        install(SyncableComponents)

        GearyInitializeEvent(geary).post()
    }.start()

    private fun createEngineModule(level: Level): GearyModule {
        val engine = HollowEngineModule(useSynchronized = true)

        return GearyModule(
            module {
                single { level }
                singleOf(::MinecraftEntityLookup)
                singleOf(::MinecraftEngine) withOptions {
                    bind<Engine>()
                    bind<ArchetypeEngine>()
                }
                includes(engine.module)
            },
            engine.properties
        )
    }
}

class GearyInitializeEvent(val geary: Geary): Event