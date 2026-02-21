package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.api.extensions.PlayerExtension
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.client.kool.addons.InventoryPicker
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.npcs.navigation.block
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForItemStackJson
import ru.hollowhorizon.hollowengine.common.utils.rl

@Serializable
@SerialName("hollowengine:player/get_health")
class PlayerHealthBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val player by input<Player>()

    override suspend fun execute(): Any? {
        return player().health
    }

    override fun InputSlotScope.composeContent() {
        Text("Здоровье игрока") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/get_max_health")
class PlayerMaxHealthBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val player by input<Player>()

    override suspend fun execute(): Any? {
        return player().maxHealth
    }

    override fun InputSlotScope.composeContent() {
        Text("Макс. здоровье игрока") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/heal")
class PlayerHealBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()
    val amount by input<Number>()

    override suspend fun execute() {
        player().heal(amount().toFloat())
    }

    override fun InputSlotScope.composeContent() {
        Text("Вылечить") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("на") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(amount)
    }
}

@Serializable
@SerialName("hollowengine:player/set_health")
class PlayerSetHealthBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()
    val health by input<Number>()

    override suspend fun execute() {
        player().health = health().toFloat()
    }

    override fun InputSlotScope.composeContent() {
        Text("Установить здоровье") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("равным") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(health)
    }
}

@Serializable
@SerialName("hollowengine:player/give_xp_points")
class PlayerGiveXpPointsBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()
    val amount by input<Number>()

    override suspend fun execute() {
        player().giveExperiencePoints(amount().toInt())
    }

    override fun InputSlotScope.composeContent() {
        Text("Выдать") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(amount)
        Text("единиц опыта игроку") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/give_xp_levels")
class PlayerGiveXpLevelsBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()
    val levels by input<Number>()

    override suspend fun execute() {
        player().giveExperienceLevels(levels().toInt())
    }

    override fun InputSlotScope.composeContent() {
        Text("Выдать") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(levels)
        Text("уровней опыта игроку") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/remove_xp_levels")
class PlayerRemoveXpLevelsBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()
    val levels by input<Number>()

    override suspend fun execute() {
        player().giveExperienceLevels(-levels().toInt())
    }

    override fun InputSlotScope.composeContent() {
        Text("Снять") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(levels)
        Text("уровней опыта у игрока") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/get_xp_points")
class GetPlayerXpPointsBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val player by input<Player>()
    override suspend fun execute(): Any? {
        return player().totalExperience
    }

    override fun InputSlotScope.composeContent() {
        Text("очки опыта игрока") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/get_xp_levels")
class GetPlayerXpLevelsBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val player by input<Player>()
    override suspend fun execute(): Any? {
        return player().experienceLevel
    }

    override fun InputSlotScope.composeContent() {
        Text("уровни опыта игрока") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/close_gui")
class PlayerCloseGuiBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()

    override suspend fun execute() {
        (player() as PlayerExtension).`hollowcore$closeContainer`()
    }

    override fun InputSlotScope.composeContent() {
        Text("Закрыть интерфейс у") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/set_respawn")
class PlayerSetRespawn : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()
    val dimension by input<String>()
    val pos by input<Vec3>()
    val forced by input<Boolean>()
    val sendMessage by input<Boolean>()

    override suspend fun execute() {
        val player = player() as ServerPlayer
        val dimension = ResourceKey.create(Registries.DIMENSION, dimension().rl)
        player.setRespawnPosition(dimension, pos().block, player.yHeadRot, true, false)
    }

    override fun InputSlotScope.composeContent() {
        Column(Grow.Std) {
            var isExpanded by remember(false)
            Row(Grow.Std) {
                Text("Возрождать") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                InputSlot(player)
                Text("в") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                InputSlot(dimension)
                Text("на") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                InputSlot(pos)
                Box(Grow.Std) {}
                Arrow(if (isExpanded) ArrowScope.ROTATION_DOWN else ArrowScope.ROTATION_RIGHT) {
                    modifier.onClick { isExpanded = !isExpanded }
                        .size(Dimensions.PaddingNormal.scaled() * 1.5f, Dimensions.PaddingNormal.scaled() * 1.5f)
                        .alignY(AlignmentY.Center)
                        .margin(horizontal = Dimensions.PaddingSmall.scaled())
                        .dragListener(object : Draggable {})
                        .colors(arrowColor = Color.WHITE.mulRgb(0.9f), arrowHoverColor = Color.WHITE)
                }
            }
            if (isExpanded) {
                Box { modifier.margin(Dimensions.PaddingSmall.scaled() * 0.5f) }
                Row(Grow.Std) {
                    Text("Принудительно: ") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                    Box(Grow.Std) {  }
                    InputSlot(forced)
                }
                Box { modifier.margin(Dimensions.PaddingSmall.scaled() * 0.5f) }
                Row(Grow.Std) {
                    Text("Уведомить игрока? ") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                    Box(Grow.Std) {  }
                    InputSlot(sendMessage)
                }
            }
        }
    }
}



@Serializable
@SerialName("hollowengine:player/has_item")
class PlayerHasItemBlock() : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()
    val player by input<Player>()
    var item: @Serializable(ForItemStackJson::class) ItemStack = ItemStack.EMPTY

    @Transient
    val popup = AutoPopup(true, true)

    override suspend fun execute(): Any? {
        val p = player()
        //? if > 1.20.1 {
        /*return p.inventory.items.any { ItemStack.isSameItemSameComponents(it, item) && it.count >= item.count }
        *///?} else {
        return p.inventory.items.any { ItemStack.isSameItemSameTags(it, item) && it.count >= item.count }
        //?}
    }

    override fun InputSlotScope.composeContent() {
        Text("Игрок") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("имеет предмет") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(horizontal = Dimensions.PaddingSmall.scaled()).bold()
        }
        Box(Dimensions.PaddingHuge.scaled() * 1.5f, Dimensions.PaddingHuge.scaled() * 1.5f) {
            modifier.alignY(AlignmentY.Center)
                .border(RoundRectBorder(Color.WHITE, Dimensions.PaddingSmall.scaled(), Dimensions.PaddingSmall.scaled()))

            Item(item) {
                val isHovered by modifier.hoverable()
                val size by animateFloatAsState(if (isHovered) 1.5f else 1.2f)

                modifier.size(Dimensions.PaddingHuge.scaled() * size, Dimensions.PaddingHuge.scaled() * size)
                    .align(AlignmentX.Center, AlignmentY.Center)
                    .onClick {
                        popup.popupContent = {
                            InventoryPicker.select {
                                item = it
                                popup.hide()
                            }
                        }

                        popup.show(Vec2f(uiNode.rightPx + Dimensions.PaddingSmall.scaled().px, uiNode.topPx))
                    }
            }
        }
        popup()
    }
}

@Serializable
@SerialName("hollowengine:player/remove_item")
class PlayerRemoveItemBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()
    var item: @Serializable(ForItemStackJson::class) ItemStack = ItemStack.EMPTY

    @Transient
    val popup = AutoPopup(true, true)

    override suspend fun execute() {
        val p = player()
        val inventory = p.inventory
        for (i in 0 until inventory.containerSize) {
            val stackInSlot = inventory.getItem(i)

            //? if > 1.20.1 {
            /*if (ItemStack.isSameItemSameComponents(stackInSlot, item)) {
            *///?} else {
            if (ItemStack.isSameItemSameTags(stackInSlot, item)) {
            //?}
                if (stackInSlot.count > item.count) {
                    stackInSlot.shrink(item.count)
                    inventory.setItem(i, stackInSlot)
                } else {
                    inventory.setItem(i, ItemStack.EMPTY)
                }
                break
            }
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("Удалить из инвентаря") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("предмет") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(horizontal = Dimensions.PaddingSmall.scaled()).bold()
        }
        Box(Dimensions.PaddingHuge.scaled() * 1.5f, Dimensions.PaddingHuge.scaled() * 1.5f) {
            modifier.alignY(AlignmentY.Center)
                .border(RoundRectBorder(Color.WHITE, Dimensions.PaddingSmall.scaled(), Dimensions.PaddingSmall.scaled()))

            Item(item) {
                val isHovered by modifier.hoverable()
                val size by animateFloatAsState(if (isHovered) 1.5f else 1.2f)

                modifier.size(Dimensions.PaddingHuge.scaled() * size, Dimensions.PaddingHuge.scaled() * size)
                    .align(AlignmentX.Center, AlignmentY.Center)
                    .onClick {
                        popup.popupContent = {
                            InventoryPicker.select {
                                item = it
                                popup.hide()
                            }
                        }

                        popup.show(Vec2f(uiNode.rightPx + Dimensions.PaddingSmall.scaled().px, uiNode.topPx))
                    }
            }
        }
        popup()
    }
}