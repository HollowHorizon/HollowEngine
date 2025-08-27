package ru.hollowhorizon.hc.common.registry

import ru.hollowhorizon.hc.HollowCore.MODID
import ru.hollowhorizon.hc.common.utils.rl
import ru.hollowhorizon.hc.common.multiblock.Multiblock

@Registry
object MultiblockRegistry : CoreRegistry<Multiblock>("$MODID:multiblock".rl)