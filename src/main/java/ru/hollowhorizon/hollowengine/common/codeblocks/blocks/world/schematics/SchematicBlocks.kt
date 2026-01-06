package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.world.schematics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.nbt.NbtIo
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.DefaultText
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentFile
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.common.world.stuctures.schematics.SchematicParser
import ru.hollowhorizon.hollowengine.common.world.stuctures.schematics.SchematicPlacer

@Serializable
@SerialName("hollowengine:world/schematic/place")
class PlaceSchematicBlock : StatementBlock() {
    val world by input<ResourceKey<Level>>("world")
    val schematic by input<String>("schematic")
    val position by input<BlockPos>("pos")

    override suspend fun execute() {
        val level = currentFile().system.owner.getLevel(world()) ?: return
        val resource = currentFile().system.owner.resourceManager.getResource(schematic().rl)
            .orElseThrow()

        val schematic = resource.open().use { inputStream ->
            val nbt = NbtIo.readCompressed(inputStream)
            SchematicParser.parse(nbt)
        }

        SchematicPlacer.place(level, position(), schematic)
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("Разместить схематику")
        InputSlot(schematic)
        DefaultText("по координатам")
        InputSlot(position)
        DefaultText("в мире")
        InputSlot(world)
    }

}