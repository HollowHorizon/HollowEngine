package ru.hollowhorizon.hollowengine.client.gui.npcs

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