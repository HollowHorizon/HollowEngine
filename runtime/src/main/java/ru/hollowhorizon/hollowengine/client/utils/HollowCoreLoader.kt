package ru.hollowhorizon.hollowengine.client.utils

//? if forge {
/*import net.minecraftforge.fml.loading.FMLConfig
*///?} else if neoforge {
/*import net.neoforged.fml.loading.FMLConfig
*///?}

import ru.hollowhorizon.hollowengine.common.config.Config
import ru.hollowhorizon.hollowengine.common.config.ConfigName
import ru.hollowhorizon.hollowengine.common.config.PropertyComment
import ru.hollowhorizon.hollowengine.common.config.PropertyName

@ConfigName("hollowengine-loader")
object HollowCoreLoader : Config() {
    @PropertyName("enable_renderdoc")
    @PropertyComment("Enable RenderDoc Extension")
    val enableRenderDoc by property(false)

    @PropertyName("opengl_version")
    @PropertyComment("OpenGL version for Kool (3.3 or 4.5)")
    var openGlVersion by property("3.3")

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