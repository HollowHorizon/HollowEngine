package ru.hollowhorizon.hollowengine.fabric

import ru.hollowhorizon.hollowengine.client.HollowCoreClient

object HCInit {
    @JvmStatic
    fun onCommonInitialize() {
        CoreInitialization
    }

    @JvmStatic
    fun onClientInitialize() {
        HollowCoreClient

    }
}
