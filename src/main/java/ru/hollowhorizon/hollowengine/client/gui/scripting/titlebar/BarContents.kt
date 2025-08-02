package ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar

import de.fabmax.kool.KeyValueStore
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.client.kool.minecraft.Image
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors
import ru.hollowhorizon.hollowengine.common.scripting.kool.KoolClientManager

@SubscribeEvent
fun leftBarContents(event: TitleBarCreationEvent.Start) = event.append {
    Image("hollowengine:textures/gui/icons/logo.png") {
        modifier.size(24.dp, 24.dp).alignY(AlignmentY.Center).margin(horizontal = sizes.smallGap)
    }

    val overlay = remember { ItemPopupMenu<Unit>("Title-File-Overlay") }
    overlay()
    TextButton("File") {
        overlay.hide()
        overlay.show(Vec2f(it.screenPosition), SubMenuItem {
            item("Новый проект") {

            }
            item("Открыть проект") {

            }
            item("Экспортировать проект") {

            }
            item("Закрыть проект") {

            }
            divider()
            item("Перезагрузить ресурсы", "hollowengine:textures/gui/icons/reload_mc.png") {
                Minecraft.getInstance().reloadResourcePacks()
            }
            item("Сбросить индексы") {
            }
            divider()
            item("Выход", "hollowengine:textures/gui/icons/exit.png") {
                Minecraft.getInstance().screen?.onClose()
            }
        }, Unit)
    }
    TextButton("Edit")
    TextButton("Search")
    TextButton("Settings")
}

fun UiScope.TextButton(text: String, onClick: (PointerEvent) -> Unit = {}) {
    Box {

        modifier.padding(horizontal = sizes.smallGap)
        modifier.background(
            RoundRectBackground(
                hoverColors(
                    color = colors.background,
                    hoverColor = IdeTheme.hoveredColors.background
                ), sizes.smallGap
            )
        )

        Text(text) {
            modifier.alignY(AlignmentY.Center)
                .onClick {
                    onClick(it)
                }
        }
    }
}

@SubscribeEvent
fun rightBarContents(event: TitleBarCreationEvent.End) = event.append {
    if (IdeContent.files.isEmpty()) return@append

    val items = IdeContent.files.filter { it.value is TextFileData }.map { (key, file) ->
        key to Composable {
            Row {
                Box {
                    modifier.alignY(AlignmentY.Center)
                    Image(file.icon) {
                        modifier.margin(end = sizes.smallGap).size(24.dp, 24.dp)
                            .imageSize(ImageSize.Stretch)
                    }
                }

                Box {
                    Text(file.fileName) {
                        modifier
                            .alignY(AlignmentY.Center)
                            .textColor(colors.onBackground)
                    }
                }
            }
        }
    }
    val itemIndex = remember { mutableStateOf(KeyValueStore.getInt("ide.file_index", -1)) }

    ComboBox.apply {
        comboBox("Empty", items.map { it.second }, itemIndex)
    }

    if (itemIndex.use() != -1) Box {
        val file = items[itemIndex.use()].first

        if (file in KoolClientManager) {
            ActionButton(24.dp, "hollowengine:textures/gui/icons/stop.png") {
                //StopScriptPacket(file).send()
            }
        } else {
            ActionButton(24.dp, "hollowengine:textures/gui/icons/play.png") {
                //StartScriptPacket(file).send()
            }
        }
    }
}

private fun UiScope.ActionButton(
    buttonSize: Dimension,
    icon: String,
    action: () -> Unit
) {
    modifier.padding(horizontal = sizes.smallGap)
        .background(
            RoundRectBackground(
                hoverColors(
                    color = colors.background,
                    hoverColor = IdeTheme.hoveredColors.background
                ), sizes.smallGap
            )
        )
        .onClick {
            action()
        }

    Image(icon) {
        modifier.size(buttonSize, buttonSize).alignY(AlignmentY.Center)
    }
}

