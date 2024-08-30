package ru.hollowhorizon.hollowengine.client.gui.npcs

import imgui.ImColor
import imgui.ImGui
import imgui.flag.ImGuiCol
import net.minecraft.locale.Language
import net.minecraft.resources.ResourceLocation

open class Tree(val name: String) {
    val childen = HashMap<String, Tree>()

    fun insert(value: String, result: ResourceLocation) {
        val values = value.split('/', limit = 2)
        if (values.size < 2) childen[values[0]] = Leaf(result)
        else childen.computeIfAbsent(values[0]) { Tree(values[0]) }.insert(values[1], result)
    }

    fun drawMenu(): ResourceLocation? {
        var result: ResourceLocation? = null
        for ((name, child) in childen.toSortedMap()) {
            if (child is Leaf) {
                if (ImGui.menuItem(
                        Language.getInstance()
                            .getOrDefault("nodes.${child.value.namespace}.${child.value.path.replace('/', '.')}")
                    )
                ) {
                    result = child.value
                }
                if (child != childen.toSortedMap().values.last()) ImGui.separator()
            } else {
                //? if >=1.20.1 {
                val color = Language.getInstance().getOrDefault("node_colors.$name", "#ffffffff")
                //?} else {

                /*val color = Language.getInstance().getOrDefault("node_colors.$name")

                *///?}
                val style = ImGui.getStyle()
                val textColor = style.getColor(ImGuiCol.Text)
                style.setColor(ImGuiCol.Text, ImColor.rgba(color))
                if (ImGui.beginMenu(Language.getInstance().getOrDefault("node_categories.$name"))) {
                    val res = child.drawMenu()
                    if (result == null) result = res
                    ImGui.endMenu()
                }
                if (child != childen.toSortedMap().values.last()) ImGui.separator()
                style.setColor(ImGuiCol.Text, textColor.x, textColor.y, textColor.z, textColor.w)
            }
        }
        return result
    }

    class Leaf(val value: ResourceLocation) : Tree("Leaf")
}

fun Collection<ResourceLocation>.toTree(): Tree {
    val root = Tree("ROOT")

    for (item in this.sorted()) {
        root.insert(item.path, item)
    }
    return root
}