package ru.hollowhorizon.hollowengine.common.scripting.kool

import de.fabmax.kool.scene.Scene
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hc.client.utils.currentServer
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendContext
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.defaultImports

@KotlinScript(
    displayName = "kool Event",
    fileExtension = "kool.kts",
    compilationConfiguration = KoolConfiguration::class
)
abstract class KoolEvent: Scene() {
    init {
        clearColor = null
        clearDepth = false
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