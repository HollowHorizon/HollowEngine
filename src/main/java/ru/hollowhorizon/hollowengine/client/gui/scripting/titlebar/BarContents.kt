package ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hc.client.kool.minecraft.Image
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors

@SubscribeEvent
fun leftBarContents(event: TitleBarCreationEvent.Start) = event.append {
    Image("hollowengine:textures/gui/icons/logo.png") {
        modifier.size(24.dp, 24.dp).alignY(AlignmentY.Center).margin(horizontal = sizes.smallGap)
    }
    TextButton("File")
    TextButton("Edit")
    TextButton("Search")
    TextButton("Settings")
}

fun UiScope.TextButton(text: String, onClick: () -> Unit = {}) {
    Box {

        modifier.padding(horizontal = sizes.smallGap)
        modifier.background(RoundRectBackground(hoverColors(color=colors.background, hoverColor=IdeTheme.hoveredColors.background), sizes.smallGap))

        Text(text) {
            modifier.alignY(AlignmentY.Center)
        }
    }
}

@SubscribeEvent
fun rightBarContents(event: TitleBarCreationEvent.End) = event.append {
    if (IdeContent.files.isEmpty()) return@append

    ComboBox.apply {
        comboBox("Empty", IdeContent.files.values.map { file ->
            Composable {
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
        })
    }

    Image("hollowengine:textures/gui/icons/play.png") {
        modifier.size(24.dp, 24.dp).alignY(AlignmentY.Center).margin(horizontal = sizes.smallGap)
    }
}

