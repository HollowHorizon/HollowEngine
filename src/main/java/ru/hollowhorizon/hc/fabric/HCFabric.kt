//? if fabric {
package ru.hollowhorizon.hc.fabric

import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.impl.launch.FabricLauncherBase
import net.irisshaders.iris.api.v0.IrisApi
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.HollowCoreClient
import ru.hollowhorizon.hc.client.utils.areShadersEnabled_
import ru.hollowhorizon.hc.client.utils.shouldOverrideShaders
import ru.hollowhorizon.hc.common.registry.createRegistry
import ru.hollowhorizon.hc.common.utils.JavaHacks
import ru.hollowhorizon.hc.fabric.internal.IrisHelper
import ru.hollowhorizon.hc.fabric.internal.NetworkHelper
import ru.hollowhorizon.hc.fabric.internal.RegistryHolderFabric

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