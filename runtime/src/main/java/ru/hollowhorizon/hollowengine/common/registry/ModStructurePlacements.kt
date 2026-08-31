package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.world.structures.placement.SingleSurfaceStructurePlacement

object ModStructurePlacements : HollowRegistry(HollowEngine.MODID) {
    val SINGLE_SURFACE: StructurePlacementType<SingleSurfaceStructurePlacement> by register(
        "single_surface",
        autoModel = null,
    ) { SingleSurfaceStructurePlacement.TYPE }
}
