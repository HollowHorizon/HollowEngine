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
import net.minecraft.nbt.TagParser
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.StringValueBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf

@Serializable
@SerialName("hollowengine:nbt/new_compound")
class NbtNewCompoundBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    @Transient
    override val expressionType: ExpressionType = typeOf<CompoundTag>()

    override suspend fun execute(): CompoundTag = CompoundTag()

    override fun InputSlotScope.composeContent() {
        Text("{}") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:nbt/parse_compound")
class NbtParseCompoundBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val snbt by inputDefault<String>("snbt") { StringValueBlock("{}") }

    @Transient
    override val expressionType: ExpressionType = typeOf<CompoundTag>()

    override suspend fun execute(): CompoundTag {
        val raw = snbt()
        return runCatching { TagParser.parseTag(raw) }.getOrDefault(CompoundTag())
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.nbt_parse".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(snbt)
    }
}

@Serializable
@SerialName("hollowengine:nbt/to_snbt")
class NbtToSnbtBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val tag by input<CompoundTag>("tag")

    @Transient
    override val expressionType: ExpressionType = typeOf<String>()

    override suspend fun execute(): String = tag().toString()

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.nbt_to_snbt".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(tag)
    }
}

@Serializable
@SerialName("hollowengine:nbt/contains")
class NbtContainsKeyBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val tag by input<CompoundTag>("tag")
    val key by input<String>("key")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val k = key()
        if (k.isBlank()) return false
        return tag().contains(k)
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(tag)
        Text("hollowengine.gui.codeblocks.label.nbt_contains".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(key)
    }
}

@Serializable
@SerialName("hollowengine:nbt/remove")
class NbtRemoveKeyBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val tag by input<CompoundTag>("tag")
    val key by input<String>("key")

    @Transient
    override val expressionType: ExpressionType = typeOf<CompoundTag>()

    override suspend fun execute(): CompoundTag {
        val copy = tag().copy()
        val k = key()
        if (k.isNotBlank()) copy.remove(k)
        return copy
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.nbt_remove".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(key)
        Text("hollowengine.gui.codeblocks.label.nbt_from".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(tag)
    }
}

@Serializable
@SerialName("hollowengine:nbt/merge")
class NbtMergeBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val a by input<CompoundTag>("a")
    val b by input<CompoundTag>("b")

    @Transient
    override val expressionType: ExpressionType = typeOf<CompoundTag>()

    override suspend fun execute(): CompoundTag {
        val left = a().copy()
        left.merge(b())
        return left
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.nbt_merge".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(a)
        Text("+") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(b)
    }
}

@Serializable
@SerialName("hollowengine:nbt/get_string")
class NbtGetStringBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val tag by input<CompoundTag>("tag")
    val key by input<String>("key")

    @Transient
    override val expressionType: ExpressionType = typeOf<String>()

    override suspend fun execute(): String = tag().getString(key())

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.nbt_get_string".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(key)
        Text("hollowengine.gui.codeblocks.label.nbt_from".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(tag)
    }
}

@Serializable
@SerialName("hollowengine:nbt/get_int")
class NbtGetIntBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val tag by input<CompoundTag>("tag")
    val key by input<String>("key")

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()

    override suspend fun execute(): Number = tag().getInt(key())

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.nbt_get_int".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(key)
        Text("hollowengine.gui.codeblocks.label.nbt_from".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(tag)
    }
}

@Serializable
@SerialName("hollowengine:nbt/get_boolean")
class NbtGetBooleanBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val tag by input<CompoundTag>("tag")
    val key by input<String>("key")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean = tag().getBoolean(key())

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.nbt_get_boolean".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(key)
        Text("hollowengine.gui.codeblocks.label.nbt_from".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(tag)
    }
}

@Serializable
@SerialName("hollowengine:nbt/get_compound")
class NbtGetCompoundBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val tag by input<CompoundTag>("tag")
    val key by input<String>("key")

    @Transient
    override val expressionType: ExpressionType = typeOf<CompoundTag>()

    override suspend fun execute(): CompoundTag = tag().getCompound(key())

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.nbt_get_compound".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(key)
        Text("hollowengine.gui.codeblocks.label.nbt_from".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(tag)
    }
}

@Serializable
@SerialName("hollowengine:nbt/set_string")
class NbtSetStringBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val tag by input<CompoundTag>("tag")
    val key by input<String>("key")
    val value by input<String>("value")

    @Transient
    override val expressionType: ExpressionType = typeOf<CompoundTag>()

    override suspend fun execute(): CompoundTag {
        val copy = tag().copy()
        copy.putString(key(), value())
        return copy
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.nbt_set".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(key)
        Text("=") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(value)
    }
}

@Serializable
@SerialName("hollowengine:nbt/set_int")
class NbtSetIntBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val tag by input<CompoundTag>("tag")
    val key by input<String>("key")
    val value by input<Number>("value")

    @Transient
    override val expressionType: ExpressionType = typeOf<CompoundTag>()

    override suspend fun execute(): CompoundTag {
        val copy = tag().copy()
        copy.putInt(key(), value().toInt())
        return copy
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.nbt_set".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(key)
        Text("=") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(value)
    }
}

@Serializable
@SerialName("hollowengine:nbt/set_boolean")
class NbtSetBooleanBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val tag by input<CompoundTag>("tag")
    val key by input<String>("key")
    val value by input<Boolean>("value")

    @Transient
    override val expressionType: ExpressionType = typeOf<CompoundTag>()

    override suspend fun execute(): CompoundTag {
        val copy = tag().copy()
        copy.putBoolean(key(), value())
        return copy
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.nbt_set".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(key)
        Text("=") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(value)
    }
}

@Serializable
@SerialName("hollowengine:nbt/set_compound")
class NbtSetCompoundBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NBT

    val tag by input<CompoundTag>("tag")
    val key by input<String>("key")
    val value by input<CompoundTag>("value")

    @Transient
    override val expressionType: ExpressionType = typeOf<CompoundTag>()

    override suspend fun execute(): CompoundTag {
        val copy = tag().copy()
        copy.put(key(), value().copy())
        return copy
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.nbt_set".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(key)
        Text("=") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(value)
    }
}
