//? if fabric {
package ru.hollowhorizon.hollowengine.fabric

import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.impl.launch.FabricLauncherBase
import net.irisshaders.iris.api.v0.IrisApi
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.HollowCoreClient
import ru.hollowhorizon.hollowengine.client.utils.areShadersEnabled_
import ru.hollowhorizon.hollowengine.client.utils.shouldOverrideShaders
import ru.hollowhorizon.hollowengine.common.registry.createRegistry
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper
import ru.hollowhorizon.hollowengine.fabric.internal.NetworkHelper
import ru.hollowhorizon.hollowengine.fabric.internal.RegistryHolderFabric

object HCFabric {
    @JvmStatic
    fun onCommonInitialize() {
        createRegistry = { rl, reg, bool, f, a ->
            RegistryHolderFabric(rl, JavaHacks.forceCast(reg), bool, JavaHacks.forceCast(f), a)
        }

        CoreInitializationFabric
        HollowCore
        FabricEvents

        NetworkHelper.register()
    }

    @JvmStatic
    fun onClientInitialize() {
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            areShadersEnabled_ = IrisApi.getInstance().config::areShadersEnabled
            shouldOverrideShaders = IrisHelper::shouldOverrideShaders
        } else {
            areShadersEnabled_ = { false }
            shouldOverrideShaders = { false }
        }

        FabricClientEvents
        HollowCoreClient

    }
}
//?}