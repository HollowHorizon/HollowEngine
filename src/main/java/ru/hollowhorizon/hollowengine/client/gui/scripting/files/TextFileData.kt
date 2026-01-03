package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.modules.ui2.*
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons

class TextFileData(name: String, path: String) : FileData(name, path) {
    override val icon: ResourceLocation = icons.FILE

    constructor(path: String, ignored: ByteArray) : this(path.substringAfterLast('/'), path)

    override fun save() {

    }

    override fun UiScope.compose() {
        Column(Grow.Std, Grow.Std) {
            modifier.margin(Dimensions.PaddingMedium)

            Text("• Добавить возможность скрывать блоки\n" +
                    "• Добавить перетаскивание блоков из панели справа\n" +
                    "• Выделение нескольких блоков\n" +
                    "• Undo/Redo, Copy/Paste\n" +
                    "• Глобальные/Локальные переменные и триггеры\n") {}
        }
    }

}