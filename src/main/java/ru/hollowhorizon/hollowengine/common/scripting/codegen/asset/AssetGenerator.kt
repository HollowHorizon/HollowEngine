package ru.hollowhorizon.hollowengine.common.scripting.codegen.asset

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager

interface AssetGenerator<T> {
    val fileExtensions: List<String>
    fun generate(manager: ResourceManager, location: ResourceLocation): T
    fun generateCode(location: ResourceLocation, generated: T): String
}