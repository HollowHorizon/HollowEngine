package ru.hollowhorizon.hollowengine.common.scripting.story.functions.effects

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.vfx.Vfx
import ru.hollowhorizon.hollowengine.common.vfx.VfxHandle

/**
 * Plays effect from a script.
 *
 * The handle that comes back is what steers the effect afterward: stopping it, moving it, or writing
 * into its data store, so its expressions can read the value as `d.<name>`.
 *
 * ```kotlin
 * val aura = boss.playVfx("mypack:vfx/aura")
 * aura[Charge] = 0.5f
 * aura.stop()
 * ```
 */
fun Level.playVfx(position: Vec3, effect: String): VfxHandle = Vfx.play(this, position, effect)

fun Level.playVfx(position: BlockPos, effect: String): VfxHandle =
    Vfx.play(this, Vec3(position.x + 0.5, position.y.toDouble(), position.z + 0.5), effect)

/** Plays [effect] on this entity; it follows it and stops when the entity is gone. */
fun Entity.playVfx(effect: String): VfxHandle = Vfx.play(this, effect)
