package ru.hollowhorizon.hollowengine.neoforge

//? if neoforge {

/*import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.HollowCoreClient
import ru.hollowhorizon.hollowengine.client.utils.areShadersEnabled_
import ru.hollowhorizon.hollowengine.client.utils.shouldOverrideShaders
import ru.hollowhorizon.hollowengine.common.registry.createRegistry
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks
import ru.hollowhorizon.hollowengine.common.utils.ModList
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient
import ru.hollowhorizon.hollowengine.neoforge.internal.NeoForgeNetworkHelper
import ru.hollowhorizon.hollowengine.neoforge.internal.RegistryHolderNeoForge

// TODO: Добавить регистрацию тегов
// TODO: Починить миксины для отрисовки интерфейсов и оверлеев

@Mod("hollowengine")
class HollowCoreNeoForge(modBus: IEventBus) {
    init {
        commonInit(modBus)

        if (isPhysicalClient) clientInit(modBus)
    }

    private fun commonInit(modBus: IEventBus) {
        createRegistry =
            { location, registry, modelType, value, type ->
                RegistryHolderNeoForge(
                    modBus,
                    location,
                    JavaHacks.forceCast(registry),
                    modelType,
                    JavaHacks.forceCast(value),
                    type
                )
            }

        CoreInitializationNeoForge
        NeoForgeEvents(modBus)
        HollowCore // Loading Main Class

        modBus.addListener(NeoForgeNetworkHelper::register)
    }

    private fun clientInit(modBus: IEventBus) {
        if (ModList.isLoaded("iris") || ModList.isLoaded("oculus")) {
            //areShadersEnabled_ = IrisApi.getInstance().config::areShadersEnabled
            //shouldOverrideShaders = IrisHelper::shouldOverrideShaders
        } else {
            areShadersEnabled_ = { false }
            shouldOverrideShaders = { false }
        }

        NeoForgeClientEvents(modBus)
        HollowCoreClient
    }
}

*///?}