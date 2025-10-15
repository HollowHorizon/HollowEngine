package ru.hollowhorizon.hollowengine.common.components

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level


interface ComponentDispatcher {
    val `hollowcore$components`: MutableMap<ResourceLocation, Component<*>>
}

val ComponentDispatcher.components: Map<ResourceLocation, Component<*>>
    get() = `hollowcore$components`
val ComponentDispatcher.isClient: Boolean
    get() = when (this) {
        is Entity -> level().isClientSide
        is Level -> isClientSide
        is DedicatedServer -> true
        else -> false
    }
val Component<*>.isClientSide: Boolean
    get() = (owner as ComponentDispatcher).isClient