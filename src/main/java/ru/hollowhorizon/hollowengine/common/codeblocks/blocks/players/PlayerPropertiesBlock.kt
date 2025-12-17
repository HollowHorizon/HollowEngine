package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.api.extensions.PlayerExtension
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.client.kool.addons.InventoryPicker
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForItemStackJson

@Serializable
@SerialName("hollowengine:player/get_health")
class PlayerHealthBlock : ExpressionBlock() {
    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val player by input<Player>()

    override suspend fun BlockContext.execute(): Any? {
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
    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val player by input<Player>()

    override suspend fun BlockContext.execute(): Any? {
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
    val player by input<Player>()
    val amount by input<Number>()

    override suspend fun BlockContext.execute() {
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
@SerialName("hollowengine:player/hurt")
class PlayerHurtBlock : StatementBlock() {
    val player by input<Player>()
    val amount by input<Number>()

    override suspend fun BlockContext.execute() {
        val p = player()
        p.hurt(p.damageSources().generic(), amount().toFloat())
    }

    override fun InputSlotScope.composeContent() {
        Text("Нанести урона") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("количество:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(amount)
    }
}

@Serializable
@SerialName("hollowengine:player/set_health")
class PlayerSetHealthBlock : StatementBlock() {
    val player by input<Player>()
    val health by input<Number>()

    override suspend fun BlockContext.execute() {
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
    val player by input<Player>()
    val amount by input<Number>()

    override suspend fun BlockContext.execute() {
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
    val player by input<Player>()
    val levels by input<Number>()

    override suspend fun BlockContext.execute() {
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
    val player by input<Player>()
    val levels by input<Number>()

    override suspend fun BlockContext.execute() {
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
    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val player by input<Player>()
    override suspend fun BlockContext.execute(): Any? {
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
    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val player by input<Player>()
    override suspend fun BlockContext.execute(): Any? {
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
    val player by input<Player>()

    override suspend fun BlockContext.execute() {
        (player() as PlayerExtension).`hollowcore$closeContainer`()
    }

    override fun InputSlotScope.composeContent() {
        Text("Закрыть интерфейс у") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/get_position")
class GetPlayerPositionBlock : ExpressionBlock() {
    @Transient
    override val expressionType: ExpressionType = typeOf<Vec3>()
    val player by input<Player>()

    override suspend fun BlockContext.execute(): Any? {
        return player().position()
    }

    override fun InputSlotScope.composeContent() {
        Text("Позиция игрока") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/has_item")
class PlayerHasItemBlock(): ExpressionBlock() {
    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()
    val player by input<Player>()
    var item: @Serializable(ForItemStackJson::class) ItemStack = ItemStack.EMPTY

    @Transient
    val popup = AutoPopup(true, true)

    override suspend fun BlockContext.execute(): Any? {
        val p = player()
        return p.inventory.items.any { ItemStack.isSameItemSameTags(it, item) && it.count >= item.count }
    }

    override fun InputSlotScope.composeContent() {
        Text("Игрок") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("имеет предмет") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(horizontal=sizes.smallGap).bold() }
        Box(sizes.largeGap * 1.5f, sizes.largeGap * 1.5f) {
            modifier.alignY(AlignmentY.Center)
                .border(RoundRectBorder(Color.WHITE, sizes.smallGap, sizes.borderWidth))

            Item(item) {
                val isHovered by modifier.hoverable()
                val size by animateFloatAsState(if (isHovered) 1.5f else 1.2f)

                modifier.size(sizes.largeGap * size, sizes.largeGap * size)
                    .align(AlignmentX.Center, AlignmentY.Center)
                    .onClick {
                        popup.popupContent = {
                            InventoryPicker.select {
                                item = it
                                popup.hide()
                            }
                        }

                        popup.show(Vec2f(uiNode.rightPx + sizes.smallGap.px, uiNode.topPx))
                    }
            }
        }
        popup()
    }
}

@Serializable
@SerialName("hollowengine:player/remove_item")
class PlayerRemoveItemBlock: StatementBlock() {
    val player by input<Player>()
    var item: @Serializable(ForItemStackJson::class) ItemStack = ItemStack.EMPTY

    @Transient
    val popup = AutoPopup(true, true)

    override suspend fun BlockContext.execute() {
        val p = player()
        val inventory = p.inventory
        for (i in 0 until inventory.containerSize) {
            val stackInSlot = inventory.getItem(i)
            if (ItemStack.isSameItemSameTags(stackInSlot, item)) {
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
        Text("предмет") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(horizontal = sizes.smallGap).bold() }
        Box(sizes.largeGap * 1.5f, sizes.largeGap * 1.5f) {
            modifier.alignY(AlignmentY.Center)
                .border(RoundRectBorder(Color.WHITE, sizes.smallGap, sizes.borderWidth))

            Item(item) {
                val isHovered by modifier.hoverable()
                val size by animateFloatAsState(if (isHovered) 1.5f else 1.2f)

                modifier.size(sizes.largeGap * size, sizes.largeGap * size)
                    .align(AlignmentX.Center, AlignmentY.Center)
                    .onClick {
                        popup.popupContent = {
                            InventoryPicker.select {
                                item = it
                                popup.hide()
                            }
                        }

                        popup.show(Vec2f(uiNode.rightPx + sizes.smallGap.px, uiNode.topPx))
                    }
            }
        }
        popup()
    }
}