package ru.hollowhorizon.hollowengine.common.compat

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.StringTag
import java.io.File

fun main() {
    val tag = CompoundTag()
    val text = File("C:\\Users\\Artem\\Downloads\\custom_npc.js").readText()
    println(StringTag.valueOf(text))
}