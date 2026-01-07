package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.margin
import de.fabmax.kool.modules.ui2.remember
import net.minecraft.core.registries.BuiltInRegistries
import ru.hollowhorizon.hollowengine.client.gui.markdown.MarkdownEditor
import ru.hollowhorizon.hollowengine.client.gui.markdown.MarkdownEditorHandler
import ru.hollowhorizon.hollowengine.generated.Assets


class MarkdownEditorPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.markdown", dock) {
    override val icon = Assets.Hollowengine.Textures.Gui.Icons.RECIPES

    override fun UiScope.compose() {
        modifier.margin(sizes.smallGap)

        val text = """
            # Заголовок
            Это обычный текст, который будет автоматически переноситься, если экран слишком узкий. Попробуйте изменить размер окна!
            
            ![Картинка](path/to/image.png)
            
            А это текст после картинки.
            
            ```kotlin
            fun helloWorld() {
                println("Hello world!")
            }
            ```
        """.trimIndent()

        MarkdownEditor(remember { MarkdownEditorHandler(text) })
    }

    companion object {
        val RECIPE_TYPES = BuiltInRegistries.RECIPE_TYPE.toList()
    }
}


