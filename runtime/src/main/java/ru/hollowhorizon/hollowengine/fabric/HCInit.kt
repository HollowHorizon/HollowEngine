//? if fabric {
package ru.hollowhorizon.hollowengine.fabric

import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.HollowCoreClient
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.VanillaInstancingBackend
import ru.hollowhorizon.hollowengine.client.utils.InstancingEntityInfo
import ru.hollowhorizon.hollowengine.client.utils.instancingBackendProvider
import ru.hollowhorizon.hollowengine.client.utils.instancingEntityInfoProvider
import ru.hollowhorizon.hollowengine.common.utils.ModList
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper

object HCInit {
    @JvmStatic
    fun onCommonInitialize() {
        CoreInitialization
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
