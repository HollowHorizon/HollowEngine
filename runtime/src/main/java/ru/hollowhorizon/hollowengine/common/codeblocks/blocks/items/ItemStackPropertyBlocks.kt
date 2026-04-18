package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.items

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.utils.areItemsEqual
import ru.hollowhorizon.hollowengine.common.utils.areStacksEqual

@Serializable
@SerialName("hollowengine:item/is_empty")
class ItemStackIsEmptyBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    val stack by input<ItemStack>("stack")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean = stack().isEmpty

    override fun InputSlotScope.composeContent() {
        InputSlot(stack)
        Text("hollowengine.gui.codeblocks.block.item_is_empty".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
    }
}

@Serializable
@SerialName("hollowengine:item/get_count")
class ItemStackGetCountBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    val stack by input<ItemStack>("stack")

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()

    override suspend fun execute(): Number = stack().count

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.item_get_count".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(stack)
    }
}

@Serializable
@SerialName("hollowengine:item/get_max_stack_size")
class ItemStackGetMaxStackSizeBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    val stack by input<ItemStack>("stack")

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()

    override suspend fun execute(): Number = stack().maxStackSize

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.item_max_stack".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(stack)
    }
}

@Serializable
@SerialName("hollowengine:item/is_damageable")
class ItemStackIsDamageableBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    val stack by input<ItemStack>("stack")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean = stack().isDamageableItem

    override fun InputSlotScope.composeContent() {
        InputSlot(stack)
        Text("hollowengine.gui.codeblocks.block.item_is_damageable".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
    }
}

@Serializable
@SerialName("hollowengine:item/get_damage")
class ItemStackGetDamageBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    val stack by input<ItemStack>("stack")

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()

    override suspend fun execute(): Number = stack().damageValue

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.item_get_damage".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(stack)
    }
}

@Serializable
@SerialName("hollowengine:item/get_max_damage")
class ItemStackGetMaxDamageBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    val stack by input<ItemStack>("stack")

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()

    override suspend fun execute(): Number = stack().maxDamage

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.item_get_max_damage".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(stack)
    }
}

@Serializable
@SerialName("hollowengine:item/get_durability")
class ItemStackGetDurabilityBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    val stack by input<ItemStack>("stack")

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()

    override suspend fun execute(): Number {
        val s = stack()
        return (s.maxDamage - s.damageValue).coerceAtLeast(0)
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.item_get_durability".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(stack)
    }
}

@Serializable
@SerialName("hollowengine:item/are_items_equal")
class ItemStackAreItemsEqualBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    val a by input<ItemStack>("a")
    val b by input<ItemStack>("b")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean = a().areItemsEqual(b())

    override fun InputSlotScope.composeContent() {
        InputSlot(a)
        Text("=") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(b)
        Text("hollowengine.gui.codeblocks.block.item_are_items_equal".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
    }
}

@Serializable
@SerialName("hollowengine:item/are_stacks_equal")
class ItemStackAreStacksEqualBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    val a by input<ItemStack>("a")
    val b by input<ItemStack>("b")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean = a().areStacksEqual(b())

    override fun InputSlotScope.composeContent() {
        InputSlot(a)
        Text("=") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(b)
        Text("hollowengine.gui.codeblocks.block.item_same_item_and_tag".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
    }
}

@Serializable
@SerialName("hollowengine:item/is_food")
class ItemStackIsFoodBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    val stack by input<ItemStack>("stack")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val s = stack()
        return s.item.components().has(net.minecraft.core.component.DataComponents.FOOD)

    }

    override fun InputSlotScope.composeContent() {
        InputSlot(stack)
        Text("hollowengine.gui.codeblocks.block.item_is_food".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
    }
}

@Serializable
@SerialName("hollowengine:item/get_rarity")
class ItemStackGetRarityBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    val stack by input<ItemStack>("stack")

    @Transient
    override val expressionType: ExpressionType = typeOf<String>()

    override suspend fun execute(): String {
        val r: Rarity = stack().rarity
        return r.name
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.item_get_rarity".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(stack)
    }
}

@Serializable
@SerialName("hollowengine:item/get_id")
class ItemStackGetIdBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    val stack by input<ItemStack>("stack")

    @Transient
    override val expressionType: ExpressionType = typeOf<String>()

    override suspend fun execute(): String {
        val key: ResourceLocation = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack().item)
        return key.toString()
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.item_get_id".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(stack)
    }
}
