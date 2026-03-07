package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.types.GetOverworldBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.types.PositionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.prefabs.PrefabSystem
import ru.hollowhorizon.hollowengine.common.utils.currentServer

@Serializable
@SerialName("hollowengine:npc/spawn_prefab")
class SpawnNpcPrefabBlock(
    val prefabPath: String = "",
) : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NPCS

    @Transient
    override val expressionType = typeOf<NpcEntity>()

    val pos by inputDefault<Vec3>("pos") { PositionBlock() }
    val dimension by inputDefault<ResourceKey<Level>>("dimension") { GetOverworldBlock() }

    override suspend fun execute(): Any? {
        val server: MinecraftServer = currentServer
        val level = server.getLevel(dimension())
            ?: error("Dimension ${dimension().location()} is not loaded!")

        val spawnPos = pos()
        val npc = NpcEntity(level).apply {
            setPos(spawnPos.x, spawnPos.y, spawnPos.z)
            moveTo(spawnPos.x, spawnPos.y, spawnPos.z, 0f, 0f)
            refreshDimensions()
            level.addFreshEntity(this)
        }

        if (prefabPath.isBlank()) return npc

        PrefabSystem.resolveOrNull(prefabPath)?.let { EntitySerialization.apply(npc, it) }
        return npc
    }

    override fun InputSlotScope.composeContent() {
        Column(Grow.Std) {
            Row(Grow.Std) {
                Text("${"hollowengine.gui.codeblocks.block.spawn_prefab".lang} ${prefabPath.removeSuffix(".entity.prefab").ifBlank { "null" }}") {
                    modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
                }
            }
            Box { modifier.margin(Dimensions.PaddingNormal.scaled()) }
            Row(Grow.Std) {
                Text("hollowengine.gui.codeblocks.label.npc_dimension".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                Box(Grow.Std) {}
                InputSlot(dimension)
            }
            Box { modifier.margin(Dimensions.PaddingNormal.scaled()) }
            Row(Grow.Std) {
                Text("hollowengine.gui.codeblocks.label.npc_position".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                Box(Grow.Std) {}
                InputSlot(pos)
            }
        }
    }
}

