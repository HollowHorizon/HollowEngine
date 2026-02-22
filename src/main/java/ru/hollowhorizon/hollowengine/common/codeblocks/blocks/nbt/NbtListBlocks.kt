package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.nbt

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf

@Serializable
@SerialName("hollowengine:nbt/new_list")
class NbtNewListBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    @Transient
    override val expressionType: ExpressionType = typeOf<ListTag>()

    override suspend fun execute(): ListTag = ListTag()

    override fun InputSlotScope.composeContent() {
        Text("[]") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:nbt/list_size")
class NbtListSizeBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val list by input<ListTag>("list")

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()

    override suspend fun execute(): Number = list().size

    override fun InputSlotScope.composeContent() {
        Text("размер") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(list)
    }
}

@Serializable
@SerialName("hollowengine:nbt/list_get_string")
class NbtListGetStringBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val list by input<ListTag>("list")
    val index by input<Number>("index")

    @Transient
    override val expressionType: ExpressionType = typeOf<String>()

    override suspend fun execute(): String {
        val i = index().toInt()
        val tag = list().getOrNull(i)
        return (tag as? StringTag)?.asString ?: tag?.asString ?: ""
    }

    override fun InputSlotScope.composeContent() {
        Text("String") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(list)
        Text("[") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(index)
        Text("]") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:nbt/list_get_compound")
class NbtListGetCompoundBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val list by input<ListTag>("list")
    val index by input<Number>("index")

    @Transient
    override val expressionType: ExpressionType = typeOf<CompoundTag>()

    override suspend fun execute(): CompoundTag {
        val i = index().toInt()
        val tag = list().getOrNull(i)
        return (tag as? CompoundTag)?.copy() ?: CompoundTag()
    }

    override fun InputSlotScope.composeContent() {
        Text("Compound") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(list)
        Text("[") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(index)
        Text("]") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:nbt/list_add_string")
class NbtListAddStringBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val list by input<ListTag>("list")
    val value by input<String>("value")

    @Transient
    override val expressionType: ExpressionType = typeOf<ListTag>()

    override suspend fun execute(): ListTag {
        val out = list().copy()
        out.add(StringTag.valueOf(value()))
        return out
    }

    override fun InputSlotScope.composeContent() {
        Text("Добавить String") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(value)
        Text("в") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(list)
    }
}

@Serializable
@SerialName("hollowengine:nbt/list_remove")
class NbtListRemoveBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val list by input<ListTag>("list")
    val index by input<Number>("index")

    @Transient
    override val expressionType: ExpressionType = typeOf<ListTag>()

    override suspend fun execute(): ListTag {
        val out = list().copy()
        val i = index().toInt()
        if (i in 0 until out.size) out.removeAt(i)
        return out
    }

    override fun InputSlotScope.composeContent() {
        Text("Удалить") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(index)
        Text("из") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(list)
    }
}

private fun ListTag.copy(): ListTag {
    val out = ListTag()
    for (i in 0 until size) {
        out.add(this[i].copy())
    }
    return out
}
