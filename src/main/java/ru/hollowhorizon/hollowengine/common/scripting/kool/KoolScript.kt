package ru.hollowhorizon.hollowengine.common.scripting.kool

import de.fabmax.kool.pipeline.ClearColorDontCare
import de.fabmax.kool.pipeline.ClearDepthDontCare
import de.fabmax.kool.scene.Scene
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.defaultImports

@KotlinScript(
    displayName = "kool Event",
    fileExtension = "kool.kts",
    compilationConfiguration = KoolConfiguration::class
)
abstract class KoolScript : Scene() {
    var tag = CompoundTag()

    init {
        clearColor = ClearColorDontCare
        clearDepth = ClearDepthDontCare

        onUpdate {
            isVisible = Minecraft.getInstance().level != null
        }
    }
}

class KoolConfiguration : HollowScriptConfiguration({
    defaultImports(
        "de.fabmax.kool.modules.ui2.*",
        "de.fabmax.kool.util.*",
        "de.fabmax.kool.modules.ui2.docking.*",
        "ru.hollowhorizon.hc.client.utils.*"
    )
})