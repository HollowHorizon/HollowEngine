package ru.hollowhorizon.hollowengine.common.scripting.annotations

@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
annotation class Import(val file: String)

/**
 * Attaches a `*.node.kts` script to a host type beyond the default [net.minecraft.server.MinecraftServer].
 *
 * `@file:Attach("net.minecraft.world.entity.LivingEntity")` adds [value] as an implicit receiver, so the
 * script body can call members of the bound entity directly and the entity-specific handlers
 * (`onInteract`, `onHurt`, `onDie`, ...) become available. Such a node must be started via
 * `/he scripting attach <entity> ...`.
 *
 * [value] is the fully-qualified class name.
 */
@Target(AnnotationTarget.FILE)
annotation class Attach(val value: String)

@Target(AnnotationTarget.FUNCTION)
annotation class State(val name: String = "")
