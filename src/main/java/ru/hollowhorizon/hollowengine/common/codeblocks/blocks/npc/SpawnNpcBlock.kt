package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.*
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.npc

@Serializable
@SerialName("hollowengine:npc/spawn")
class SpawnNpcBlock : CodeBlock(), ExpressionBlock {
    @Transient
    override val expressionType = AnyType

    var npcName: String = "Guide"
    var modelPath: String = "hollowengine:models/entity/player_model.gltf"

    override suspend fun execute(context: BlockContext): Any? {
        val pos = inputs["pos"]?.execute(context) as? Vec3 ?: Vec3(0.0, 0.0, 0.0)

        val entity = npc(
            pos = pos,
            name = npcName,
            model = modelPath,
        )

        return entity
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Column {
            Text("Создать NPC") { modifier.textColor(Color.WHITE) }

            Row {
                Text("Имя:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
                TextField(npcName) {
                    modifier.width(100.dp).margin(start = 5.dp).onChange { npcName = it }
                }
            }

            TextField(modelPath) {
                modifier.width(150.dp).hint("Путь к модели").onChange { modelPath = it }
            }

            Row {
                Text("Позиция:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
                InputSlot("pos", ExpressionTypes.VEC3)
            }
        }
    }
}