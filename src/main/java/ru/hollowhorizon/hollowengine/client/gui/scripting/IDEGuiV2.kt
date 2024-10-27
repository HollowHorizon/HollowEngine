package ru.hollowhorizon.hollowengine.client.gui.scripting

import imgui.ImGui
import imgui.type.ImBoolean
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.imgui.Renderable
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

object IDEGuiV2 : Renderable {
    private val cache = ImBoolean(false)

    override fun Graphics.render() {
        var ideGui by remember { false }
        cache.set(ideGui)
        if (ImGui.begin("IDE V2", cache)) {
            textShadow("This is IDE V2")
        }
        ideGui = cache.get()
        ImGui.end()
    }
}

fun <T> Graphics.remember(value: () -> T): ReadWriteProperty<Any?, T> {
    return object : ReadWriteProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return ImGui.getStateStorage().getBool(property.name.hashCode()) as T
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            ImGui.getStateStorage().setBool(property.name.hashCode(), value as Boolean)
        }
    }
}