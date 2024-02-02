package ru.hollowhorizon.hollowengine.common.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.util.Mth
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.structure.StructurePiece
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.capabilities.StructuresCapability
import ru.hollowhorizon.hollowengine.common.structures.ScriptedStructure


fun ServerLevel.getSafeSpawn(position: BlockPos): BlockPos {
    var d0 = -1.0
    val xPos: Int = position.x
    val yPos: Int = position.y
    val zPos: Int = position.z
    var xPosCopy = xPos
    var yPosCopy = yPos
    var zPosCopy = zPos
    var l1 = 0
    val i2: Int = random.nextInt(4)
    val mutable: BlockPos.MutableBlockPos = BlockPos.MutableBlockPos()

    for (j2 in xPos - 16..xPos + 16) {
        val d1 = j2.toDouble() + 0.5 - position.x

        for (l2 in zPos - 16..zPos + 16) {
            val d2 = l2.toDouble() + 0.5 - position.z

            var height: Int = height - 1
            heightLoop@ while (height >= 0) {
                if (isEmptyBlock(mutable.set(j2, height, l2))) {
                    while (height > 0 && isEmptyBlock(mutable.set(j2, height - 1, l2))) --height

                    for (k3 in i2 until i2 + 4) {
                        var l3 = k3 % 2
                        var i4 = 1 - l3
                        if (k3 % 4 >= 2) {
                            l3 = -l3
                            i4 = -i4
                        }

                        for (j4 in 0..2) {
                            for (k4 in 0..3) {
                                for (l4 in -1..3) {
                                    val i5 = j2 + (k4 - 1) * l3 + j4 * i4
                                    val j5 = height + l4
                                    val k5 = l2 + (k4 - 1) * i4 - j4 * l3
                                    mutable.set(i5, j5, k5)
                                    if (l4 < 0 && !getBlockState(mutable).material.isSolid || l4 >= 0 && !isEmptyBlock(
                                            mutable
                                        )
                                    ) {
                                        --height
                                        continue@heightLoop
                                    }
                                }
                            }
                        }

                        val d5 = height.toDouble() + 0.5 - position.y
                        val d7 = d1 * d1 + d5 * d5 + d2 * d2
                        if (d0 < 0.0 || d7 < d0) {
                            d0 = d7
                            xPosCopy = j2
                            yPosCopy = height
                            zPosCopy = l2
                            l1 = k3 % 4
                        }
                    }
                }
                --height
            }
        }
    }

    if (d0 < 0.0) {
        for (l5 in xPos - 16..xPos + 16) {
            val d3 = l5.toDouble() + 0.5 - position.x

            for (j6 in zPos - 16..zPos + 16) {
                val d4 = j6.toDouble() + 0.5 - position.z

                var height: Int = height - 1
                heightLoop@ while (height >= 0) {
                    if (isEmptyBlock(mutable.set(l5, height, j6))) {
                        while (height > 0 && isEmptyBlock(mutable.set(l5, height - 1, j6))) --height

                        for (l7 in i2 until i2 + 2) {
                            val l8 = l7 % 2
                            val k9 = 1 - l8

                            for (i10 in 0..3) {
                                for (k10 in -1..3) {
                                    val i11 = l5 + (i10 - 1) * l8
                                    val j11 = height + k10
                                    val k11 = j6 + (i10 - 1) * k9
                                    mutable.set(i11, j11, k11)
                                    if (k10 < 0 && !getBlockState(mutable).material.isSolid || k10 >= 0 && !isEmptyBlock(
                                            mutable
                                        )
                                    ) {
                                        --height
                                        continue@heightLoop
                                    }
                                }
                            }

                            val d6 = height.toDouble() + 0.5 - position.y
                            val d8 = d3 * d3 + d6 * d6 + d4 * d4
                            if (d0 < 0.0 || d8 < d0) {
                                d0 = d8
                                xPosCopy = l5
                                yPosCopy = height
                                zPosCopy = j6
                                l1 = l7 % 2
                            }
                        }
                    }
                    --height
                }
            }
        }
    }

    val i6 = xPosCopy
    var k2 = yPosCopy
    val k6 = zPosCopy
    var l6 = l1 % 2
    var i3 = 1 - l6
    if (l1 % 4 >= 2) {
        l6 = -l6
        i3 = -i3
    }

    if (d0 < 0.0) {
        yPosCopy = Mth.clamp(yPosCopy, 70, height - 10)
        k2 = yPosCopy

        for (j7 in -1..1) {
            for (i8 in 1..2) {
                for (i9 in -1..2) {
                    val l9 = i6 + (i8 - 1) * l6 + j7 * i3
                    val j10 = k2 + i9
                    val l10 = k6 + (i8 - 1) * i3 - j7 * l6
                    val flag = i9 < 0
                    mutable.set(l9, j10, l10)
                    level.setBlockAndUpdate(
                        mutable,
                        if (flag) Blocks.OBSIDIAN.defaultBlockState() else Blocks.AIR.defaultBlockState()
                    )
                }
            }
        }
    }

    for (k7 in -1..2) {
        for (j8 in -1..3) {
            if (k7 == -1 || k7 == 2 || j8 == -1 || j8 == 3) mutable.set(i6 + k7 * l6, k2 + j8, k6 + k7 * i3)
        }
    }

    for (k8 in 0..1) {
        for (j9 in 0..2) mutable.set(i6 + k8 * l6, k2 + j9 - 2, k6 + k8 * i3)
    }

    return mutable
}

fun ServerLevel.getStructure(path: String, pos: BlockPos = BlockPos.ZERO): StructureWrapper {
    val location = "hollowengine:$path"
    val capability = this[StructuresCapability::class].structures
    if (!capability.contains(location)) {
        level.findNearestMapStructure(
            TagKey.create(Registry.STRUCTURE_REGISTRY, location.rl),
            pos, 100, false
        )
    }
    val spos = capability["hollowengine:$path"] ?: throw IllegalStateException("Structure $path not found!")
    val bpos = BlockPos(spos.x, spos.y, spos.z)
    val structures = level.structureManager().getAllStructuresAt(bpos)
    val structure = structures.mapNotNull { it.key as? ScriptedStructure }.first { it.location.path == path }
    return StructureWrapper(this, bpos, level.structureManager().getStructureAt(bpos, structure).pieces.first())
}

class StructureWrapper(
    val level: ServerLevel,
    val pos: BlockPos,
    val first: StructurePiece
) {
    fun getLocalPos(x: Int = 0, y: Int = 0, z: Int = 0): BlockPos {
        return BlockPos(pos.x + x, pos.y + y, pos.z + z)
    }
}