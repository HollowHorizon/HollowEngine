package ru.hollowhorizon.hollowengine.common.components

import net.minecraft.resources.ResourceLocation


interface ComponentDispatcher {
    val `hollowcore$components`: MutableMap<ResourceLocation, Component<*>>
}