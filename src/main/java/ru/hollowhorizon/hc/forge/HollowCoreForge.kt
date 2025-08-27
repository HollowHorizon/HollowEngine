package ru.hollowhorizon.hc.forge//? if forge {
/*package ru.hollowhorizon.hc.forge

import net.irisshaders.iris.api.v0.IrisApi
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.loading.FMLLoader
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.HollowCoreClient
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hc.common.utils.*
import ru.hollowhorizon.hc.common.registry.createRegistry
import ru.hollowhorizon.hc.fabric.internal.IrisHelper
import ru.hollowhorizon.hc.forge.internal.ForgeNetworkHelper
import ru.hollowhorizon.hc.forge.internal.RegistryHolderForge

@Mod("hollowcore")
class HollowCoreForge {
    init {
        commonInit()

        if (isPhysicalClient) clientInit()
    }

    private fun commonInit() {
        createRegistry =
            { location, registry, modelType, value, type ->
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
        HollowCore // Loading Main Class

        ForgeNetworkHelper.register()
    }

    private fun clientInit() {
        if (ModList.isLoaded("iris") || ModList.isLoaded("oculus")) {
            areShadersEnabled_ = IrisApi.getInstance().config::areShadersEnabled
            shouldOverrideShaders = IrisHelper::shouldOverrideShaders
        } else {
            areShadersEnabled_ = { false }
            shouldOverrideShaders = { false }
        }

        ForgeClientEvents
        HollowCoreClient
    }
} *///?}
