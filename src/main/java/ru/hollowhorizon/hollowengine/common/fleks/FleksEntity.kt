package ru.hollowhorizon.hollowengine.common.fleks

import com.github.quillraven.fleks.World
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import com.github.quillraven.fleks.Entity as FleksEntity

interface FleksWorld {
    val `hollowengine$fleksWorld`: World
}

interface FleksEntity {
    val `hollowengine$fleksEntity`: FleksEntity
}

val Level.fleks get() = (this as FleksWorld).`hollowengine$fleksWorld`
val Entity.fleks get() = (this as ru.hollowhorizon.hollowengine.common.fleks.FleksEntity).`hollowengine$fleksEntity`
