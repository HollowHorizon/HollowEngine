package ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hc.client.kool.minecraft.Image
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme

@SubscribeEvent
fun leftBarContents(event: TitleBarCreationEvent.Start) = event.append {
    Image("hollowengine:textures/gui/icons/logo.png") {
        modifier.size(24.dp, 24.dp).alignY(AlignmentY.Center).margin(horizontal=sizes.smallGap)
    }
    TextButton("File")
    TextButton("Edit")
    TextButton("Search")
    TextButton("Settings")
}

fun UiScope.TextButton(text: String, onClick: () -> Unit = {}) {
    Box {
        var isHovered by remember { mutableStateOf(false) }
        modifier.padding(horizontal=sizes.smallGap).onEnter { isHovered = true }.onExit { isHovered = false }
        if (isHovered) modifier.background(RoundRectBackground(IdeTheme.hoveredColors.background, sizes.smallGap))

        Text(text) {
            modifier.alignY(AlignmentY.Center)
        }
    }
}

@SubscribeEvent
fun rightBarContents(event: TitleBarCreationEvent.End) = event.append {
    if(IdeContent.files.isEmpty()) return@append

    ComboBox {
        modifier.colors(
            textBackgroundColor = colors.background.mulRgb(1.3f),
            expanderColor = colors.background.mulRgb(1.5f),
            expanderArrowColor = Color.WHITE
        )
        modifier.padding(0.dp).width(115.dp)
    }

    Image("hollowengine:textures/gui/icons/play.png") {
        modifier.size(24.dp, 24.dp).alignY(AlignmentY.Center).margin(horizontal=sizes.smallGap)
    }
}

