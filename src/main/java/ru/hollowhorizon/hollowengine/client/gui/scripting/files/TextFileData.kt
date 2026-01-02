package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.modules.ui2.*
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons

class TextFileData(name: String, path: String) : FileData(name, path) {
    override val icon: ResourceLocation = icons.FILE

    constructor(path: String, ignored: ByteArray) : this(path.substringAfterLast('/'), path)

    override fun save() {

    }

    override fun UiScope.compose() {
        Column(Grow.Std, Grow.Std) {
            modifier.margin(Dimensions.PaddingMedium)

            Text("Халва, не забудь сделать форматирование текста!\n") {}
            Text("А то ну тут пока как-то скудно...\n") {}
            Text("• А лучше сделать парсер MarkDown :D\n") {}

            Row {
                modifier.alignX(AlignmentX.Center)
                Text("Ну а пока, всех с наступающим ") {
                    modifier.font(sizes.normalText.derive(40f))
                }
                Image(icons.CHRISTMAS_TREE) {
                    modifier.size(Dimensions.PaddingExtraLarge, Dimensions.PaddingExtraLarge)
                        .alignY(AlignmentY.Center)
                }
                Text("!") {
                    modifier.font(sizes.normalText.derive(40f))
                }
            }

            Text("(Да это ёлка)") {
                modifier.font(sizes.normalText.derive(12f))
                    .textColor(ColorTheme.UI.BackgroundAccent)
                    .alignX(AlignmentX.Center)
            }
            Text("(И да, HollowEngine, оказывается, поддерживает эмодзи)") {
                modifier.font(sizes.normalText.derive(12f))
                    .textColor(ColorTheme.UI.BackgroundAccent)
                    .alignX(AlignmentX.Center)
            }
        }
    }

}