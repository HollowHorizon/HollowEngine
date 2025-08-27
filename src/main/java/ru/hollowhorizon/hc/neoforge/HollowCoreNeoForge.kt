package ru.hollowhorizon.hc.neoforge

//? if neoforge {

/*import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.HollowCoreClient
import ru.hollowhorizon.hc.client.utils.areShadersEnabled_
import ru.hollowhorizon.hc.client.utils.shouldOverrideShaders
import ru.hollowhorizon.hc.common.registry.createRegistry
import ru.hollowhorizon.hc.common.utils.JavaHacks
import ru.hollowhorizon.hc.common.utils.ModList
import ru.hollowhorizon.hc.common.utils.isPhysicalClient
import ru.hollowhorizon.hc.neoforge.internal.NeoForgeNetworkHelper
import ru.hollowhorizon.hc.neoforge.internal.RegistryHolderNeoForge

// TODO: Добавить регистрацию тегов
// TODO: Починить миксины для отрисовки интерфейсов и оверлеев

@Mod("hollowcore")
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