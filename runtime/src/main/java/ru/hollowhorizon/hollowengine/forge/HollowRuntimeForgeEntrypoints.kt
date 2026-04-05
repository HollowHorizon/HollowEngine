package ru.hollowhorizon.hollowengine.forge

//? if forge {
/*import net.irisshaders.iris.api.v0.IrisApi
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.HollowCoreClient
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.VanillaInstancingBackend
import ru.hollowhorizon.hollowengine.client.utils.InstancingEntityInfo
import ru.hollowhorizon.hollowengine.client.utils.areShadersEnabled_
import ru.hollowhorizon.hollowengine.client.utils.instancingBackendProvider
import ru.hollowhorizon.hollowengine.client.utils.instancingEntityInfoProvider
import ru.hollowhorizon.hollowengine.client.utils.shouldOverrideShaders
import ru.hollowhorizon.hollowengine.common.registry.createRegistry
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks
import ru.hollowhorizon.hollowengine.common.utils.ModList
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper
import ru.hollowhorizon.hollowengine.forge.internal.ForgeNetworkHelper
import ru.hollowhorizon.hollowengine.forge.internal.RegistryHolderForge

object HollowRuntimeForgeEntrypoints {
    @JvmStatic
    fun initialize() {
        createRegistry = { location, registry, modelType, value, type ->
            RegistryHolderForge(
                location,
                JavaHacks.forceCast(registry),
                modelType,
                JavaHacks.forceCast(value),
                type
            )
        }

        CoreInitializationForge
        ForgeEvents
        HollowCore
        ForgeNetworkHelper.register()

        if (isPhysicalClient) {
            initializeClient()
        }
    }

    private fun initializeClient() {
        if (ModList.isLoaded("iris") || ModList.isLoaded("oculus")) {
            areShadersEnabled_ = IrisApi.getInstance().config::areShadersEnabled
            shouldOverrideShaders = IrisHelper::shouldOverrideShaders
            instancingBackendProvider = {
                if (IrisHelper.shouldOverrideShaders()) IrisHelper.instancingBackend() else VanillaInstancingBackend
            }
            instancingEntityInfoProvider = {
                if (IrisHelper.shouldOverrideShaders()) IrisHelper.capturedEntityInfo() else InstancingEntityInfo()
            }
        } else {
            areShadersEnabled_ = { false }
            shouldOverrideShaders = { false }
            instancingBackendProvider = { VanillaInstancingBackend }
            instancingEntityInfoProvider = { InstancingEntityInfo() }
        }

        ForgeClientEvents
        HollowCoreClient
    }
}
*///?}
