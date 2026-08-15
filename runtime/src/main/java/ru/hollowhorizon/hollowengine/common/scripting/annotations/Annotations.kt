package ru.hollowhorizon.hollowengine.common.scripting.annotations

import kotlin.reflect.KClass

/**
 * Pulls other scripts into this one. A plain name is resolved next to the importing script, inside its
 * own namespace; a qualified `addon-id:path/lib.kts` reaches into another namespace and requires that
 * namespace to be declared in `dependsOn`.
 */
@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
annotation class Import(vararg val file: String)

/**
 * Reuses this script's instance when it is reached more than once through an import graph.
 * Unannotated imported scripts are evaluated independently for every import path.
 */
@Target(AnnotationTarget.FILE)
annotation class SharedScript

/**
 * Attaches a `*.node.kts` script to a host type beyond the default [net.minecraft.server.MinecraftServer].
 *
 * `@file:Attach(net.minecraft.world.entity.LivingEntity::class)` adds [value] as an implicit receiver, so the
 * script body can call members of the bound entity directly and the entity-specific handlers
 * (`onInteract`, `onHurt`, `onDie`, ...) become available. Such a node must be started via
 * `/he scripting attach <entity> ...`.
 *
 * [value] is the host class.
 */
@Target(AnnotationTarget.FILE)
annotation class Attach(val value: KClass<*>)

@Target(AnnotationTarget.FUNCTION)
annotation class State(val name: String = "")
