package ru.hollowhorizon.hollowengine.common.registry

import ru.hollowhorizon.hollowengine.HollowCore.MODID
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.common.multiblock.Multiblock

@Registry
object MultiblockRegistry : CoreRegistry<Multiblock>("$MODID:multiblock".rl)