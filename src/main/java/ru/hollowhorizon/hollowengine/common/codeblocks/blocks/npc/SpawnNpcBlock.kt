package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.npc

@Serializable
@SerialName("hollowengine:npc/spawn")
class SpawnNpcBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NPCS

    @Transient
    override val expressionType = typeOf<NpcEntity>()

    val pos by input<Vec3>("pos")
    val name by input<String>("npc")

    override suspend fun execute(): Any? {

        val entity = npc(
            pos = pos(),
            name = name(),
        )

        return entity
    }

    override fun InputSlotScope.composeContent() {
        Column(Grow.Std) {
            var isExpanded by remember(false)
            Row(Grow.Std) {
                Text("Создать NPC") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                Box(Grow.Std) {}
                Arrow(if (isExpanded) ArrowScope.ROTATION_DOWN else ArrowScope.ROTATION_RIGHT) {
                    modifier.onClick { isExpanded = !isExpanded }
                        .size(Dimensions.PaddingHuge.scaled(), Dimensions.PaddingHuge.scaled())
                        .alignY(AlignmentY.Center)
                        .margin(horizontal=Dimensions.PaddingNormal.scaled())
                        .dragListener(object: Draggable{})
                        .colors(arrowColor = Color.WHITE.mulRgb(0.9f), arrowHoverColor = Color.WHITE)
                }
            }
            if (isExpanded) {
                Box { modifier.margin(Dimensions.PaddingNormal.scaled()) }
                Row(Grow.Std) {
                    Text("Имя:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                    Box(Grow.Std) {}
                    InputSlot(name)
                }
                Box { modifier.margin(Dimensions.PaddingNormal.scaled()) }
                Row(Grow.Std) {
                    Text("Позиция:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                    Box(Grow.Std) { }
                    InputSlot(pos)
                }
            }
        }
    }
}