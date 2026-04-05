package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.GameType
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf

@Serializable
@SerialName("hollowengine:player/gamemode")
class PlayerGameModeBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()
    var modeInt = 0 // 0=Survival, 1=Creative, 2=Adventure, 3=Spectator

    override suspend fun execute() {
        val p = player()
        if (p is ServerPlayer) {
            val mode = when (modeInt) {
                1 -> GameType.CREATIVE
                2 -> GameType.ADVENTURE
                3 -> GameType.SPECTATOR
                else -> GameType.SURVIVAL
            }
            p.setGameMode(mode)
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_gamemode".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)

        ComboBox {
            modifier.width(FitContent).items(listOf(
                "hollowengine.gui.codeblocks.label.gamemode_survival".lang,
                "hollowengine.gui.codeblocks.label.gamemode_creative".lang,
                "hollowengine.gui.codeblocks.label.gamemode_adventure".lang,
                "hollowengine.gui.codeblocks.label.gamemode_spectator".lang
            ))
                .font(font)
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.15f), Dimensions.PaddingSmall.scaled()))
                .zLayer(modifier.zLayer + 10)
                .margin(Dimensions.PaddingSmall.scaled()).padding(Dimensions.PaddingSmall.scaled())
                .alignY(AlignmentY.Center)
            modifier.selectedIndex(modeInt)
            modifier.onItemSelected { modeInt = it }
        }
    }
}

@Serializable
@SerialName("hollowengine:player/check_gamemode")
class PlayerCheckGamemodeBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()
    val player by input<Player>()
    var modeInt = 0

    override suspend fun execute(): Any {
        val gm = (player() as ServerPlayer).gameMode.gameModeForPlayer
        return gm.id == modeInt
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("hollowengine.gui.codeblocks.label.player_in_gamemode".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        ComboBox {
            modifier.width(FitContent).items(listOf(
                "hollowengine.gui.codeblocks.label.gamemode_survival".lang,
                "hollowengine.gui.codeblocks.label.gamemode_creative".lang,
                "hollowengine.gui.codeblocks.label.gamemode_adventure".lang,
                "hollowengine.gui.codeblocks.label.gamemode_spectator".lang
            ))
                .font(font)
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.15f), Dimensions.PaddingSmall.scaled()))
                .zLayer(modifier.zLayer + 10)
                .margin(Dimensions.PaddingSmall.scaled()).padding(Dimensions.PaddingSmall.scaled())
                .alignY(AlignmentY.Center)
            modifier.selectedIndex(modeInt)
            modifier.onItemSelected { modeInt = it }
        }
    }
}
