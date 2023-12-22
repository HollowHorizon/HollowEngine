package ru.hollowhorizon.hollowengine.common.npcs.pathing

import net.minecraft.core.BlockPos
import net.minecraft.core.BlockPos.MutableBlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation
import net.minecraft.world.level.Level
import net.minecraft.world.level.pathfinder.*
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.min

class NPCPathNavigator(entity: Mob, world: Level) : GroundPathNavigation(entity, world) {
    private var pathToPosition: BlockPos? = null
    var entityWidth = Mth.floor(mob.bbWidth + 1.0f)
    var entityHeight = Mth.floor(mob.bbHeight + 1.0f)

    override fun createPathFinder(maxVisitedNodes: Int): PathFinder {
        nodeEvaluator = WalkNodeEvaluator().apply {
            setCanPassDoors(true)
            setCanOpenDoors(true)
            setCanFloat(true)
        }
        return NPCPathFinder(nodeEvaluator, maxVisitedNodes)
    }

    override fun trimPath() {
        super.trimPath()
        val path = path ?: return
        for (i in 0 until path.nodeCount) {
            val node = path.getNode(i)
            val node2 = if (i + 1 < path.nodeCount) path.getNode(i + 1) else null
            val blockState = level.getBlockState(BlockPos(node.x, node.y, node.z))
            if (!blockState.`is`(BlockTags.STAIRS)) continue
            path.replaceNode(i, node.cloneAndMove(node.x, node.y + 1, node.z))
            if (node2 == null || node.y < node2.y) continue
            path.replaceNode(i + 1, node.cloneAndMove(node2.x, node.y + 1, node2.z))
        }
    }

    override fun followThePath() {
        val path = path ?: return
        val entityPos = this.tempMobPos
        var pathLength = path.nodeCount
        for (i in path.nextNodeIndex until path.nodeCount) {
            if (path.getNode(i).y != entityPos.y.toInt()) {
                pathLength = i
                break
            }
        }
        val base = entityPos.add((-mob.bbWidth * 0.5f).toDouble(), 0.0, (-mob.bbWidth * 0.5f).toDouble())
        val max = base.add(mob.bbWidth.toDouble(), mob.bbHeight.toDouble(), mob.bbWidth.toDouble())
        if (tryShortcut(path, Vec3(mob.x, mob.y, mob.z), pathLength, base, max)) {
            if (isAt(path, 0.5f) || atElevationChange(path) && isAt(path, mob.bbWidth * 0.5f)) {
                //mob.lookControl.setLookAt(path.getNextEntityPos(mob))
                path.nextNodeIndex++
            }
        }
        doStuckDetection(entityPos)
    }

    override fun createPath(blockPos: BlockPos, i: Int): Path? {
        pathToPosition = blockPos
        return super.createPath(blockPos, i)
    }

    override fun createPath(entity: Entity, i: Int): Path? {
        pathToPosition = entity.blockPosition()
        return super.createPath(entity, i)
    }

    override fun moveTo(entity: Entity, d: Double): Boolean {
        val path = this.createPath(entity, 1)
        if (path != null) {
            return this.moveTo(path, d)
        }
        pathToPosition = entity.blockPosition()
        speedModifier = d
        return true
    }

    override fun tick() {
        super.tick()

        if (this.isDone) {
            val path = pathToPosition ?: return
            if (path.closerToCenterThan(mob.position(), mob.bbWidth.toDouble()) ||
                mob.y > path.y.toDouble() &&
                BlockPos(path.x, mob.blockPosition().y, path.z)
                    .closerToCenterThan(mob.position(), mob.bbWidth.toDouble())
            ) pathToPosition = null
            else {
                mob.moveControl.setWantedPosition(
                    path.x.toDouble() + 0.5, path.y.toDouble(), path.z.toDouble() + 0.5,
                    speedModifier
                )
            }

            return
        }
        val pos = targetPos ?: return

        //mob.lookControl.setLookAt(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
    }

    private fun isAt(path: Path, threshold: Float): Boolean {
        val pathPos = path.getNextEntityPos(mob)
        return Mth.abs((mob.x - pathPos.x).toFloat()) < threshold &&
                Mth.abs((mob.z - pathPos.z).toFloat()) < threshold &&
                abs(mob.y - pathPos.y) < 1.0
    }

    private fun atElevationChange(path: Path): Boolean {
        val curr = path.nextNodeIndex
        val end = min(
            path.nodeCount.toDouble(),
            (curr + Mth.ceil(mob.bbWidth * 0.5f) + 1).toDouble()
        ).toInt()
        val currentY = path.getNode(curr).y
        for (i in curr + 1 until end) {
            if (path.getNode(i).y != currentY) return true
        }
        return false
    }

    private fun tryShortcut(path: Path, entityPos: Vec3, pathLength: Int, base: Vec3, max: Vec3): Boolean {
        var i = pathLength
        while (--i > path.nextNodeIndex) {
            val vec = path.getEntityPosAtNode(mob, i).subtract(entityPos)
            if (sweep(vec, base, max)) {
                path.nextNodeIndex = i
                return false
            }
        }
        return true
    }

    private fun sweep(vec: Vec3, base: Vec3, max: Vec3): Boolean {
        var t = 0.0f
        val maxT = vec.length().toFloat()
        if (maxT < EPSILON) return true
        val tr = FloatArray(3)
        val ldi = IntArray(3)
        val tri = IntArray(3)
        val step = IntArray(3)
        val tDelta = FloatArray(3)
        val tNext = FloatArray(3)
        val normed = FloatArray(3)

        for (i in 0..2) {
            val value = element(vec, i)
            val dir = value >= 0.0f
            step[i] = if (dir) 1 else -1
            val lead = element(if (dir) max else base, i)
            tr[i] = element(if (dir) base else max, i)
            ldi[i] = leadEdgeToInt(lead, step[i])
            tri[i] = trailEdgeToInt(tr[i], step[i])
            normed[i] = value / maxT
            tDelta[i] = Mth.abs(maxT / value)
            val dist = if (dir) ldi[i] + 1 - lead else lead - ldi[i]
            tNext[i] = if (tDelta[i] < Float.POSITIVE_INFINITY) tDelta[i] * dist else Float.POSITIVE_INFINITY
        }

        val pos = MutableBlockPos()
        do {
            // stepForward
            val axis =
                if (tNext[0] < tNext[1]) (if (tNext[0] < tNext[2]) 0 else 2)
                else if (tNext[1] < tNext[2]) 1
                else 2
            val dt = tNext[axis] - t
            t = tNext[axis]
            ldi[axis] += step[axis]
            tNext[axis] += tDelta[axis]
            for (i in 0..2) {
                tr[i] += dt * normed[i]
                tri[i] = trailEdgeToInt(tr[i], step[i])
            }
            // checkCollision
            val stepx = step[0]
            val x0 = if (axis == 0) ldi[0] else tri[0]
            val x1 = ldi[0] + stepx
            val stepy = step[1]
            val y0 = if (axis == 1) ldi[1] else tri[1]
            val y1 = ldi[1] + stepy
            val stepz = step[2]
            val z0 = if (axis == 2) ldi[2] else tri[2]
            val z1 = ldi[2] + stepz
            var x = x0
            while (x != x1) {
                var z = z0
                while (z != z1) {
                    var y = y0
                    while (y != y1) {
                        val block = level.getBlockState(pos.set(x, y, z))
                        if (!block.isPathfindable(level, pos, PathComputationType.LAND)) return false
                        y += stepy
                    }
                    val belowPath: BlockPathTypes = nodeEvaluator.getBlockPathType(
                        level, x, y0 - 1, z, mob,
                        entityWidth, entityHeight, entityWidth,
                        true, true
                    )
                    if (belowPath == BlockPathTypes.WATER || belowPath == BlockPathTypes.LAVA || belowPath == BlockPathTypes.OPEN) return false
                    val inPath: BlockPathTypes = nodeEvaluator.getBlockPathType(
                        level, x, y0, z, mob,
                        entityWidth, entityHeight, entityWidth,
                        true, true
                    )
                    val priority = mob.getPathfindingMalus(inPath)
                    if (priority < 0.0f || priority >= 8.0f) return false
                    if (inPath == BlockPathTypes.DAMAGE_FIRE || inPath == BlockPathTypes.DANGER_FIRE || inPath == BlockPathTypes.DAMAGE_OTHER) return false
                    z += stepz
                }
                x += stepx
            }
        } while (t <= maxT)
        return true
    }

    companion object {
        const val EPSILON = 1.0E-8f
        fun leadEdgeToInt(coord: Float, step: Int): Int {
            return Mth.floor(coord - step * EPSILON)
        }

        fun trailEdgeToInt(coord: Float, step: Int): Int {
            return Mth.floor(coord + step * EPSILON)
        }

        fun element(v: Vec3, i: Int): Float {
            return when (i) {
                0 -> v.x.toFloat()
                1 -> v.y.toFloat()
                2 -> v.z.toFloat()
                else -> 0.0f
            }
        }
    }
}