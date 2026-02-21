package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.utils.*

@Serializable
@SerialName("hollowengine:component/text_component")
class TextComponentBlock : ExpressionBlock() {
    @Transient
    override val expressionType: ExpressionType = typeOf<Component>()

    val text by input<String>("text")
    val textColor = ColorHSV()
    var bold = false
    var italic = false
    var underlined = false
    var obfuscated = false
    var strikethrough = false

    override suspend fun execute(): Any? {
        return Component.literal(text())
            .colored(textColor.toColor().toIntRGB())
            .apply { if (bold) bold() }
            .apply { if (italic) italic() }
            .apply { if (underlined) underlined() }
            .apply { if (obfuscated) obfuscated() }
            .apply { if (strikethrough) strikethrough() }
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(text)
        textColor(scale)
    }
}

@Serializable
@SerialName("hollowengine:component/text_merger")
class TextMergerBlock : ExpressionBlock() {
    @Transient
    override val expressionType: ExpressionType = typeOf<Component>()

    val components by inputList<Component>("components")

    override suspend fun execute(): Any? {
        val merged = Component.empty()
        for (component in components()) {
            merged.append(component)
        }
        return merged
    }

    override fun InputSlotScope.composeContent() {
        InputSlotList("components", typeOf<Component>())
    }
}
