package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.getLevel

@Serializable
@SerialName("hollowengine:player/teleport")
class PlayerTeleportBlock : StatementBlock() {
    val player by input<Player>()
    val pos by input<Vec3>()
    val dimension by input<String>("dimension")

    override suspend fun execute() {
        val p = player()
        val target = pos()
        if (p is ServerPlayer) {
            p.teleportTo(p.server.getLevel(dimension()), target.x, target.y, target.z, p.yRot, p.xRot)
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("Телепортировать") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("в") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(dimension)
        Text("на") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(pos)
    }
}