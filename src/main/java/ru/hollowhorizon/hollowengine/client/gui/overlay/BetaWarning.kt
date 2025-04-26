package ru.hollowhorizon.hollowengine.client.gui.overlay

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hc.client.kool.KoolManager
import ru.hollowhorizon.hc.client.kool.ScreenScene
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme

object BetaWarning {
    val overlay = ScreenScene("Compilation Status").apply {
        setupUiScene()

        val sizes = Sizes.medium

        addPanelSurface(IdeTheme.colors, IdeTheme.sizes) {
            modifier.align(AlignmentX.Center, AlignmentY.Center)
                .border(RoundRectBorder(Color.WHITE, sizes.smallGap, sizes.borderWidth))
                .background(RoundRectBackground(Color("000000AA"), sizes.smallGap))
                .size(Grow(0.75f), FitContent)
                .padding(sizes.gap)

            Column(Grow.Std, FitContent) {
                Text("Добро пожаловать в HollowEngine 2.0 Beta!") {
                    modifier.alignX(AlignmentX.Center)
                }
                divider()
                Text(
                    "Использовать эту версию для реальных проектов крайне не рекомендуется. " +
                            "Обо всех багах и ошибках, просьба сообщать в Discord канале, прилагая логи, скрипты и скомпилированные файлы."
                ) {
                    modifier.isWrapText(true)
                        .width(Grow.Std)
                        .alignX(AlignmentX.Center)
                }
                Button("Я понял") {
                    modifier.alignX(AlignmentX.Center).alignY(AlignmentY.Bottom)
                        .onClick { KoolManager.context.removeScene(this@apply) }
                }
            }
        }
    }

}