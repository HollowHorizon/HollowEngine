package ru.hollowhorizon.hollowengine.common.geary.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.api.Registerable
import ru.hollowhorizon.hollowengine.api.Syncable
import ru.hollowhorizon.hollowengine.common.geary.api.GearyRuntimeState

@Serializable
enum class NameplateMode {
    HIDDEN,
    SHOW,
    SHOW_ON_HOVER,
}

@Registerable
@Syncable
@Serializable
@SerialName("hollowengine:entity/nameplate")
data class NameplateComponent(val mode: NameplateMode)

val Entity.nameplateComponent: NameplateComponent?
    get() = GearyRuntimeState.componentsById(this).values.filterIsInstance<NameplateComponent>().firstOrNull()