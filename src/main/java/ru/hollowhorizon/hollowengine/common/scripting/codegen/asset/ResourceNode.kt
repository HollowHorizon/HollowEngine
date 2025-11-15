package ru.hollowhorizon.hollowengine.common.scripting.codegen.asset

import net.minecraft.server.packs.resources.ResourceManager

sealed class ResourceNode {
    abstract val name: String
    abstract fun generateCode(manager: ResourceManager, indent: Int = 0): String
}