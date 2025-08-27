package ru.hollowhorizon.hollowengine.forge
//? if forge {
/*
import net.irisshaders.iris.api.v0.IrisApi
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.loading.FMLLoader
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.HollowCoreClient
import ru.hollowhorizon.hollowengine.client.utils.*
import ru.hollowhorizon.hollowengine.common.utils.*
import ru.hollowhorizon.hollowengine.common.registry.createRegistry
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper
import ru.hollowhorizon.hollowengine.forge.internal.ForgeNetworkHelper
import ru.hollowhorizon.hollowengine.forge.internal.RegistryHolderForge

@Mod("hollowengine")
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
