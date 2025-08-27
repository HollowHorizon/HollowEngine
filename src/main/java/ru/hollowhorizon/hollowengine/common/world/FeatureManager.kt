package ru.hollowhorizon.hollowengine.common.world

import net.minecraft.data.worldgen.BootstapContext
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature
import ru.hollowhorizon.hollowengine.HollowCore

object FeatureManager {
    fun onReload(context: BootstapContext<ConfiguredFeature<*, *>>) {
        HollowCore.LOGGER.info("Features reloading: {}",context)
    }
}