package ru.hollowhorizon.hollowengine.client.utils

//? if forge {
/*import net.minecraftforge.fml.loading.FMLConfig
*///?} else if neoforge {
/*import net.neoforged.fml.loading.FMLConfig
*///?}

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object HollowCoreLoader {
    @SerialName("enable_renderdoc")
    val enableRenderDoc = false
    @SerialName("opengl_version")
    var openGlVersion = "3.3"

    @JvmStatic
    fun canAttachRenderdoc(): Boolean {
        if (!enableRenderDoc) return false

        //? if fabric {
        return true
        //?} else {
        /*return !FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)
        *///?}
    }
}