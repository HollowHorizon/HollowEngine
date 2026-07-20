package ru.hollowhorizon.hollowengine.fabric

import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.HollowCoreClient

object HCInit {
    @JvmStatic
    fun onCommonInitialize() {
        CoreInitialization
        HollowCore
    }

    @JvmStatic
    fun onClientInitialize() {
        HollowCoreClient

    }
}
