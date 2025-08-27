package ru.hollowhorizon.hollowengine.client.utils

//? if forge {
/*import net.minecraftforge.fml.loading.FMLConfig
*///?} else if neoforge {
/*import net.neoforged.fml.loading.FMLConfig
*///?}

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.common.config.HollowConfig
import ru.hollowhorizon.hollowengine.common.config.hollowConfig

object HollowCoreLoader {
    val config by hollowConfig(::Config, "hollowcore-loader")

    @JvmStatic
    fun canAttachRenderdoc(): Boolean {
        if (!config.enableRenderDoc) return false

        //? if fabric {
        return true
        //?} else {
        /*return !FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)
        *///?}
    }

    @Serializable
    class Config : HollowConfig() {
        var enableRenderDoc = false

        @SerialName("opengl_version")
        var openGlVersion = "3.3"
    }
}