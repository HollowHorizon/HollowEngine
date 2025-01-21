package ru.hollowhorizon.hollowengine.common.scripting.gui

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.defaultImports

@KotlinScript(
    "GuiScript", "gui.kts", compilationConfiguration = GuiScriptConfiguration::class
)
abstract class GuiScript {
    val storage = CompoundTag()

}

class GuiScriptConfiguration : HollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hc.client.imgui.Graphics",
        "imgui.internal.ImGui",
        "imgui.internal.ImGui.*",
        "imgui.*",
        "ru.hollowhorizon.hc.client.utils.*"
    )
})