//? if fabric {
package ru.hollowhorizon.hollowengine.fabric

import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.HollowCoreClient
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.VanillaInstancingBackend
import ru.hollowhorizon.hollowengine.client.utils.InstancingEntityInfo
import ru.hollowhorizon.hollowengine.client.utils.instancingBackendProvider
import ru.hollowhorizon.hollowengine.client.utils.instancingEntityInfoProvider
import ru.hollowhorizon.hollowengine.common.registry.createRegistry
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks
import ru.hollowhorizon.hollowengine.common.utils.ModList
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper
import ru.hollowhorizon.hollowengine.fabric.internal.RegistryHolderFabric

object HCFabric {
    @JvmStatic
    fun onCommonInitialize() {
        createRegistry = { rl, reg, bool, f, a ->
            RegistryHolderFabric(rl, JavaHacks.forceCast(reg), bool, JavaHacks.forceCast(f), a)
        }

        CoreInitializationFabric
        HollowCore
    }

    @JvmStatic
    fun onClientInitialize() {
        if (ModList.isLoaded("iris")) {
            instancingBackendProvider = {
                if (IrisHelper.shouldOverrideShaders()) IrisHelper.instancingBackend() else VanillaInstancingBackend
            }
            instancingEntityInfoProvider = {
                if (IrisHelper.shouldOverrideShaders()) IrisHelper.capturedEntityInfo() else InstancingEntityInfo()
            }
        } else {
            instancingBackendProvider = { VanillaInstancingBackend }
            instancingEntityInfoProvider = { InstancingEntityInfo() }
        }

        HollowCoreClient

    }
}
//?}
