package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.items

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.EnchantmentHelper
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentServer
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.jvm.optionals.getOrNull

@Serializable
@SerialName("hollowengine:item/has_tag")
class ItemStackHasItemTagBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    val stack by input<ItemStack>("stack")
    val tagId by input<String>("tag")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val id = tagId().trim()
        if (id.isBlank()) return false

        val key = TagKey.create(Registries.ITEM, id.rl)
        return stack().`is`(key)
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(stack)
        Text("hollowengine.gui.codeblocks.block.item_has_tag".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(tagId)
    }
}

@Serializable
@SerialName("hollowengine:item/get_enchant_level")
class ItemStackGetEnchantLevelBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    val stack by input<ItemStack>("stack")
    val enchantmentId by input<String>("enchantment")

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()

    override suspend fun execute(): Number {
        val enchId = enchantmentId().trim()
        if (enchId.isBlank()) return 0

        val ench = currentServer().registryAccess().asGetterLookup()
            .get(Registries.ENCHANTMENT, ResourceKey.create(Registries.ENCHANTMENT, enchId.rl))
            .getOrNull() ?: return 0
        return EnchantmentHelper.getItemEnchantmentLevel(ench, stack())

    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.item_get_enchant_level".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(enchantmentId)
        Text("hollowengine.gui.codeblocks.label.variable_for".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(stack)
    }
}

@Serializable
@SerialName("hollowengine:item/has_enchant")
class ItemStackHasEnchantBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    val stack by input<ItemStack>("stack")
    val enchantmentId by input<String>("enchantment")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val enchId = enchantmentId().trim()
        if (enchId.isBlank()) return false

        val ench = currentServer().registryAccess().asGetterLookup()
            .get(Registries.ENCHANTMENT, ResourceKey.create(Registries.ENCHANTMENT, enchId.rl))
            .getOrNull() ?: return false
        return EnchantmentHelper.getItemEnchantmentLevel(ench, stack()) > 0

    }

    override fun InputSlotScope.composeContent() {
        InputSlot(stack)
        Text("hollowengine.gui.codeblocks.label.item_has_enchant".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(enchantmentId)
    }
}