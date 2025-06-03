package ru.hollowhorizon.hollowengine.common.scripting.inline

import net.minecraft.util.Mth
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import kotlin.random.Random
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    displayName = "Inline Script",
    fileExtension = "inline.kts",
    compilationConfiguration = InlineConfiguration::class
)
abstract class InlineScript {
    val math = Mth()
    val random = Random
}
class InlineConfiguration: HollowScriptConfiguration()
