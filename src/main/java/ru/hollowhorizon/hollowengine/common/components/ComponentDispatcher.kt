package ru.hollowhorizon.hollowengine.common.components

import de.fabmax.kool.util.Log.level
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level


interface ComponentDispatcher {
    val `hollowcore$components`: MutableMap<ResourceLocation, Component<*>>
}

val ComponentDispatcher.components: Map<ResourceLocation, Component<*>>
    get() = `hollowcore$components`
val ComponentDispatcher.isClient: Boolean
    get() = when(this) {
        is Entity -> level().isClientSide
        is Level -> isClientSide
        is DedicatedServer -> true
        else -> false
    }