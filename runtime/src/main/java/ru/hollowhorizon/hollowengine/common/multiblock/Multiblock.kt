package ru.hollowhorizon.hollowengine.common.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.ColorResolver
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.lighting.LevelLightEngine
import net.minecraft.world.level.material.FluidState
import ru.hollowhorizon.hollowengine.common.utils.registryAccess

/**
 * Represents a multiblock structure that can be validated within a Minecraft world.
 * This class provides methods for defining a multiblock pattern and checking if a given
 * in-world structure matches the defined pattern.
 *
 * # WARNING!
 * Multiblocks can be created ONLY INSIDE THE CHILD CLASS [ru.hollowhorizon.hollowengine.common.registry.HollowRegistry]!
 *
 * # Example:
 * ```kotlin
 * object ExampleMultiblocks: HollowRegistry {
 *     val obsidianFrame by register("obsidian_frame") {
 *         Multiblock(3, 3, 3) {
 *             val o = block(Blocks.OBSIDIAN.defaultBlockState())
 *             val d = block(Blocks.DIAMOND_BLOCK.defaultBlockState())
 *             val e = block(Blocks.EMERALD_BLOCK.defaultBlockState())
 *
 *             pattern(
 *                 // First level.
 *                 // This is the so-called foundation of the structure.
 *                 o, o, o,
 *                 o, d, o,
 *                 o, o, o,
 *
 *                 // Second level
 *                 // This is the center of the structure
 *                 o, null, o,
 *                 null, e, null,
 *                 o, null, o,
 *
 *                 // Third level
 *                 // This is the topmost structure
 *                 o, o, o,
 *                 o, d, o,
 *                 o, o, o,
 *             )
 *         }
 *     }
 * }
 *```
 *
 * @param block A lambda function used to configure the multiblock.
 */
class Multiblock(val xSize: Int, val ySize: Int, val zSize: Int, block: Multiblock.() -> Unit) : BlockAndTintGetter {
    private val tileEntities = hashMapOf<BlockPos, BlockEntity>()
    val blocks = ArrayList<Matcher>()

    init {
        block()
    }

    /**
     * Defines the pattern of blocks in the multiblock structure.
     *
     * @param blocks The array of block matchers defining the structure.
     */
    fun pattern(vararg blocks: Matcher?) {
        assert(blocks.size != xSize * ySize * zSize) { "Blocks must have the same size" }

        this.blocks.clear()
        this.blocks.addAll(blocks.map { it ?: block(Blocks.AIR.defaultBlockState()) })
    }

    /**
     * Checks if the structure is valid at the given position.
     *
     * @param level The level in which the structure is being checked.
     * @param basePos The base position to check from.
     * @return True if the structure is valid, false otherwise.
     */
    fun isValid(level: Level, basePos: BlockPos) =
        listOf(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST).any {
            checkStructureForDirection(level, basePos, it)
        }

    private fun checkStructureForDirection(level: Level, basePos: BlockPos, direction: Direction): Boolean {
        // We check all possible initial positions within the structure
        for (offsetX in 0..< xSize) {
            for (offsetY in 0..< ySize) {
                for (offsetZ in 0..< zSize) {
                    if (checkFromBase(level, basePos, direction, offsetX, offsetY, offsetZ)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun checkFromBase(
        level: Level,
        basePos: BlockPos,
        direction: Direction,
        startX: Int,
        startY: Int,
        startZ: Int,
    ): Boolean {
        for (y in 0..< ySize) {
            for (z in 0..< zSize) {
                for (x in 0..< xSize) {
                    val expectedBlock = blocks[x + z * zSize + y * zSize * xSize]
                    if (expectedBlock.default().isAir) continue

                    // Смещаем блоки относительно выбранной стартовой точки
                    val rotatedPos = getRotatedPos(basePos, x - startX, y - startY, z - startZ, direction)
                    val currentBlock = level.getBlockState(rotatedPos)

                    if (!expectedBlock.matches(currentBlock)) return false
                }
            }
        }
        return true
    }

    private fun getRotatedPos(basePos: BlockPos, x: Int, y: Int, z: Int, direction: Direction): BlockPos {
        return when (direction) {
            Direction.NORTH -> basePos.offset(x, y, -z)
            Direction.SOUTH -> basePos.offset(-x, y, z) // Вращение на 180 градусов
            Direction.WEST -> basePos.offset(-z, y, -x)   // Поворот на 90 градусов против часовой стрелки
            Direction.EAST -> basePos.offset(z, y, x)   // Поворот на 90 градусов по часовой стрелке
            else -> basePos
        }
    }

    override fun getHeight(): Int {
        return ySize
    }

    override fun getMinBuildHeight(): Int {
        return 0
    }

    override fun getBrightness(type: LightLayer, pos: BlockPos) = 14

    override fun getRawBrightness(pos: BlockPos, ambientDarkening: Int) = 15 - ambientDarkening

    override fun getBlockEntity(pos: BlockPos): BlockEntity? {
        val state = getBlockState(pos)
        if (state.block is EntityBlock) {
            return tileEntities.computeIfAbsent(pos.immutable()) { p ->
                (state.block as EntityBlock).newBlockEntity(
                    pos,
                    state
                ) ?: throw IllegalStateException("Block does not have a BlockEntity")
            }
        }
        return null
    }

    override fun getBlockState(pos: BlockPos): BlockState {
        val id = pos.x + pos.z * zSize + pos.y * zSize * xSize
        if (id !in 0..<blocks.size) return Blocks.LIGHT.defaultBlockState()
        return blocks[id].default()
    }

    override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState

    override fun getShade(direction: Direction, shade: Boolean): Float {
        return 1f
    }

    override fun getLightEngine(): LevelLightEngine? {
        return null
    }

    override fun getBlockTint(pos: BlockPos, color: ColorResolver): Int {
        return color.getColor(
            registryAccess.registry(Registries.BIOME).get().getOrThrow(Biomes.PLAINS),
            pos.x.toDouble(),
            pos.z.toDouble()
        )
    }

    /**
     * Defines a matcher interface for block validation within the multiblock structure.
     */
    interface Matcher {
        /**
         * Checks if a given block state matches this matcher.
         *
         * @param block The block state to check.
         * @return True if the block matches, false otherwise.
         */
        fun matches(block: BlockState): Boolean

        /**
         * Provides the default block state for this matcher.
         *
         * @return The default block state.
         */
        fun default(): BlockState
    }

    /**
     * Creates a matcher for a specific block state.
     *
     * @param state The block state to match.
     * @param ignoreTag Whether to ignore block tags when matching.
     * @return A matcher for the given block state.
     */
    fun block(state: BlockState, ignoreTag: Boolean = false) = object : Matcher {
        override fun matches(block: BlockState): Boolean {
            return if (ignoreTag) block.`is`(state.block) else block == state
        }

        override fun default() = state
    }

    /**
     * Creates a matcher for a tag of blocks.
     *
     * @param tag The block tag to match.
     * @return A matcher that checks if a block belongs to the given tag.
     */
    fun tag(tag: TagKey<Block>) = object : Matcher {
        override fun matches(block: BlockState) = block.`is`(tag)

        override fun default(): BlockState {
            return BuiltInRegistries.BLOCK
                .getTag(tag).get().firstOrNull()?.value()
                ?.defaultBlockState() ?: Blocks.AIR.defaultBlockState()
        }
    }
}