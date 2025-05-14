package ru.hollowhorizon.hollowengine.client.gui.scripting.files.dialog

import de.fabmax.kool.input.KeyCode
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.LocalKeyCode
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.client.kool.minecraft.Image
import ru.hollowhorizon.hc.common.coroutines.scopeSync
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.story.InlineScript
import kotlin.math.max
import kotlin.math.min
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.valueOrNull

abstract class DialogComponent<T>(var component: T) : Composable {
    abstract val icon: String
    abstract val color: Color
    abstract val title: String
}

class TextComponent : DialogComponent<String>("") {
    override val icon = "hollowengine:textures/gui/icons/file.png"
    override val color = Color.DARK_GREEN
    override val title = "Фраза"

    override fun UiScope.compose() {
        Row(Grow.Std) {
            Text("Текст: ") {
                modifier.textColor(Color.DARK_GRAY)
                    .alignY(AlignmentY.Center)
            }
            TextField {
                modifier.textColor = Color.DARK_GRAY
                modifier.cursorColor = Color.DARK_GRAY
                modifier.width(Grow.Std)
                    .alignY(AlignmentY.Center)
                    .text(component)
                    .onChange { component = it }
            }
        }
    }
}

class WaitComponent : DialogComponent<Int>(1) {
    override val icon = "hollowengine:textures/gui/icons/reload.png"
    override val color = Color.BLUE
    override val title = "Ожидание"
    private var tempText = component.toString()

    override fun UiScope.compose() {
        Row(Grow.Std) {
            Text("Время: ") {
                modifier.textColor(Color.DARK_GRAY)
                    .alignY(AlignmentY.Center)
            }
            TextField {
                modifier.textColor = Color.DARK_GRAY
                modifier.cursorColor = Color.DARK_GRAY
                modifier.width(Grow.Std)
                    .text(tempText)
                    .onChange {
                        tempText = it
                        component = 0

                        it.toIntOrNull()?.let { c ->
                            component = c
                        }
                    }
                    .onEnterPressed {
                        scopeSync {
                            val result = ScriptingCompiler.compileText<InlineScript>(tempText)
                                .execute()
                            result.valueOrNull()?.let {
                                (it.returnValue as? ResultValue.Value)?.let {
                                    (it.value as? Number)?.let {
                                        component = it.toInt()
                                        tempText = component.toString()
                                    }
                                }
                            }
                        }
                    }
                    .alignY(AlignmentY.Center)
            }
            ComboBox {
                modifier.items(listOf("Секунд", "Минут", "Часов"))
                    .alignY(AlignmentY.Center)
                    .padding(sizes.smallGap * 0.5f)
                    .margin(horizontal = sizes.smallGap)
                    .colors(
                        textBackgroundColor = Color.WHITE.mulRgb(0.8f),
                        textBackgroundHoverColor = Color.WHITE.mulRgb(0.7f),
                        expanderColor = Color.WHITE.mulRgb(0.8f),
                        expanderHoverColor = Color.WHITE.mulRgb(0.7f),
                        expanderArrowColor = Color.DARK_GRAY,
                    )
                modifier.textColor = Color.DARK_GRAY
            }
        }
    }
}

object NewComponent : DialogComponent<Unit>(Unit) {
    override val icon = "hollowengine:textures/gui/icons/add.png"
    override val color = Color.WHITE
    override val title = "Добавить"

    override fun UiScope.compose() {}
}

fun UiScope.DialogEditor(components: List<DialogComponent<*>>) {
    LazyColumn(
        scrollPaneModifier = { it.margin(end = sizes.smallGap) },
        containerModifier = { it.background(null) }
    ) {
        items(components) { component ->
            Row(Grow.Std) {
                val shader = RoundImageShader()
                Image(component.icon) {
                    modifier.onClick {
                        if (component == NewComponent) {
                            Minecraft.getInstance().setScreen(null)
                        }
                    }
                    modifier.size(Dp.fromPx(64f) + sizes.smallGap * 2, Dp.fromPx(64f) + sizes.smallGap * 2)
                    modifier.background(object : UiRenderer<UiNode> {
                        override fun renderUi(node: UiNode) {
                            node.apply {
                                val lt = max(leftPx, clipLeftPx)
                                val rt = min(rightPx, clipRightPx)
                                val tp = node.parent?.topPx ?: max(topPx, clipTopPx)
                                val bt = node.parent?.bottomPx ?: min(bottomPx, clipBottomPx)

                                node.getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                                    .rect(
                                        lt + (rt - lt) / 2 - sizes.borderWidth.px / 2, tp,
                                        sizes.borderWidth.px, bt - tp,
                                        parent?.clipBoundsPx ?: clipBoundsPx, Color("F9F8F4")
                                    )
                            }
                        }
                    }).customShader(shader).onPositioned {
                        modifier.imageProvider?.getTexture(uiNode.innerWidthPx, uiNode.innerHeightPx)?.let {
                            shader.image = it
                            shader.borderWidth = 0.075f
                            shader.borderColor = component.color.toVec4f()
                        }
                    }.padding(sizes.smallGap)
                }

                if (component == NewComponent) return@Row

                Column(Grow.Std) {
                    modifier.padding(sizes.smallGap)

                    Box(Grow.Std) {
                        modifier.background(object : UiRenderer<UiNode> {
                            override fun renderUi(node: UiNode) {
                                node.apply {
                                    val lt = max(leftPx, clipLeftPx)
                                    val rt = min(rightPx, clipRightPx)
                                    val tp = max(topPx, clipTopPx)
                                    val bt = min(bottomPx, clipBottomPx)

                                    node.getUiPrimitives(UiSurface.LAYER_BACKGROUND).apply {
                                        circle(
                                            lt,
                                            topPx + (bottomPx - topPx) / 2,
                                            (bottomPx - topPx) / 8,
                                            node.parent?.clipBoundsPx ?: clipBoundsPx,
                                            component.color.mulRgb(0.8f)
                                        )
                                        rect(lt, tp, rt - lt, bt - tp, clipBoundsPx, component.color)
                                    }
                                }
                            }
                        })
                        modifier
                            .margin(horizontal = sizes.smallGap)
                            .padding(sizes.smallGap)
                            .layout(RowLayout)

                        Text(component.title.lang) {
                        }
                        Box(Grow.Std) {}
                        CloseButton(
                            background = component.color,
                            backgroundHover = component.color.mulRgb(1.2f),
                            foreground = Color.WHITE,
                            foregroundHover = Color.WHITE
                        ) {
                            modifier.align(AlignmentX.End, AlignmentY.Center)
                        }
                    }

                    Box(Grow.Std) {
                        modifier.padding(sizes.smallGap)
                            .backgroundColor(Color("F9F8F4"))

                        component()
                    }
                }
            }
        }
    }
}