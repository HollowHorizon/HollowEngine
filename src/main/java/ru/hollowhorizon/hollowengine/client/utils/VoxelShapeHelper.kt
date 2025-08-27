/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.client.utils

import com.google.gson.JsonParser
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import java.io.InputStreamReader
import java.util.function.UnaryOperator

object VoxelShapeHelper {
    private val fromOrigin = Vec3(-0.5, -0.5, -0.5)

    fun loadFromLocation(location: ResourceLocation): VoxelShape {
        val shapes: MutableList<VoxelShape> = ArrayList()
        JsonParser.parseReader(InputStreamReader(location.stream)).asJsonObject
            .getAsJsonArray("elements")
            .forEach { element ->
                val o = element.asJsonObject
                val from = o.getAsJsonArray("from")
                val x1 = from[0].asInt
                val y1 = from[1].asInt
                val z1 = from[2].asInt
                val to = o.getAsJsonArray("to")
                val x2 = to[0].asInt
                val y2 = to[1].asInt
                val z2 = to[2].asInt
                shapes.add(
                    Block.box(
                        x1.toDouble(),
                        y1.toDouble(),
                        z1.toDouble(),
                        x2.toDouble(),
                        y2.toDouble(),
                        z2.toDouble()
                    )
                )
            }
        val root = shapes[0]
        shapes.removeAt(0)
        return Shapes.or(root, *shapes.toTypedArray<VoxelShape>())
    }

    fun VoxelShape.rotate(rotation: Rotation): VoxelShape {
        return rotate(this) { it.rotate(rotation) }
    }

    fun VoxelShape.rotate(rotation: Direction): VoxelShape {
        return rotate(this) { it.rotate(rotation) }
    }

    fun AABB.rotate(side: Direction): AABB {
        return when (side) {
            Direction.DOWN -> this
            Direction.UP -> AABB(minX, -minY, -minZ, maxX, -maxY, -maxZ)
            Direction.NORTH -> AABB(minX, -minZ, minY, maxX, -maxZ, maxY)
            Direction.SOUTH -> AABB(-minX, -minZ, -minY, -maxX, -maxZ, -maxY)
            Direction.WEST -> AABB(minY, -minZ, -minX, maxY, -maxZ, -maxX)
            Direction.EAST -> AABB(-minY, -minZ, minX, -maxY, -maxZ, maxX)
        }
    }

    fun AABB.rotate(rotation: Rotation): AABB {
        return when (rotation) {
            Rotation.NONE -> this
            Rotation.CLOCKWISE_90 -> AABB(-minZ, minY, minX, -maxZ, maxY, maxX)
            Rotation.CLOCKWISE_180 -> AABB(-minX, minY, -minZ, -maxX, maxY, -maxZ)
            Rotation.COUNTERCLOCKWISE_90 -> AABB(minZ, minY, -minX, maxZ, maxY, -maxX)
        }
    }

    private fun rotate(shape: VoxelShape, rotateFunction: UnaryOperator<AABB>): VoxelShape {
        val rotatedPieces: MutableList<VoxelShape> = ArrayList()
        //Explode the voxel shape into bounding boxes
        val sourceBoundingBoxes = shape.toAabbs()
        //Rotate them and convert them each back into a voxel shape
        for (sourceBoundingBox in sourceBoundingBoxes) {
            //Make the bounding box be centered around the middle, and then move it back after rotating
            rotatedPieces.add(
                Shapes.create(
                    rotateFunction.apply(sourceBoundingBox.move(fromOrigin.x, fromOrigin.y, fromOrigin.z))
                        .move(-fromOrigin.x, -fromOrigin.z, -fromOrigin.z)
                )
            )
        }
        //return the recombined rotated voxel shape
        return combine(rotatedPieces)
    }

    private fun combine(shapes: List<VoxelShape>): VoxelShape {
        return batchCombine(Shapes.empty(), BooleanOp.OR, true, shapes)
    }

    private fun batchCombine(
        initial: VoxelShape,
        function: BooleanOp,
        simplify: Boolean,
        shapes: Collection<VoxelShape>,
    ): VoxelShape {
        var combinedShape = initial
        for (shape in shapes) {
            combinedShape = Shapes.joinUnoptimized(combinedShape, shape, function)
        }
        return if (simplify) combinedShape.optimize() else combinedShape
    }
}
