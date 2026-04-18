package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.world.schematics

import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.DefaultText
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentFile
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.common.world.stuctures.schematics.SchematicParser
import ru.hollowhorizon.hollowengine.common.world.stuctures.schematics.SchematicPlacer

@Serializable
@SerialName("hollowengine:world/schematic/place")
class PlaceSchematicBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.STRUCTURES

    val world by input<ResourceKey<Level>>("world")
    val schematic by input<String>("schematic")
    val position by input<BlockPos>("pos")

    override suspend fun execute() {
        val level = currentFile().system.owner.getLevel(world()) ?: return
        val resource = currentFile().system.owner.resourceManager.getResource(schematic().rl)
            .orElseThrow()

        val schematic = resource.open().use { inputStream ->
            val nbt = NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap())

            SchematicParser.parse(nbt)
        }

        SchematicPlacer.place(level, position(), schematic)
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("hollowengine.gui.codeblocks.label.world_place_schematic".lang)
        InputSlot(schematic)
        DefaultText("hollowengine.gui.codeblocks.label.world_at_coords".lang)
        InputSlot(position)
        DefaultText("hollowengine.gui.codeblocks.label.world_in_world".lang)
        InputSlot(world)
    }

}