package ru.hollowhorizon.hollowengine.common.registry

import ru.hollowhorizon.hollowengine.HollowEngine.MODID
import ru.hollowhorizon.hollowengine.common.multiblock.Multiblock
import ru.hollowhorizon.hollowengine.common.registry.system.RegistryManager
import ru.hollowhorizon.hollowengine.common.utils.rl

val MultiblockRegistry = RegistryManager.create<Multiblock>("$MODID:multiblocks".rl)