package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.utils.rl

@Serializable
@SerialName("hollowengine:player/recipe_unlocked")
class PlayerHasRecipeBlock : ExpressionBlock() {
    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()
    val player by input<Player>()
    var recipeRl: String = "minecraft:crafting_table"
    var position = Vec2f.ZERO

    override suspend fun execute(): Any {
        val p = player() as ServerPlayer
        // Проверка рецепта через книгу рецептов
        return p.recipeBook.contains(recipeRl.rl)
    }

    override fun InputSlotScope.composeContent() {
        Text("Игрок") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("знает рецепт") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }

        TextField {
            modifier.text(recipeRl).width(200.dp).onChange {
                recipeRl = it
                //TODO: Add popup logic here fetching from player.level().recipeManager.recipes
            }.alignY(AlignmentY.Center).margin(start = Dimensions.PaddingSmall.scaled())
        }
    }
}