package ru.hollowhorizon.hollowstory.client.screen

import com.mojang.blaze3d.matrix.MatrixStack
import net.minecraft.util.text.StringTextComponent
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.screens.widget.OriginWidget
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.client.utils.parent
import ru.hollowhorizon.hollowstory.client.screen.imgui.MCNodeEditor
import ru.hollowhorizon.hollowstory.client.screen.widget.action.AnimeWidget
import ru.hollowhorizon.hollowstory.client.screen.widget.action.ProcedureWidget
import ru.hollowhorizon.hollowstory.client.screen.widget.action.TestProcedureWidget

open class VisualEditorScreen : HollowScreen(StringTextComponent("Visual Editor")) {
    private lateinit var editorZone: OriginWidget

    override fun init() {
        super.init()

        this.editorZone = OriginWidget(0, 0, width, height)

        TestProcedureWidget(0, 0, 100, 20) parent editorZone

        AnimeWidget(-1000, -1000, 1600, 2500) parent editorZone

        this.editorZone parent this
    }

    companion object {
        fun openGUI() {
            MCNodeEditor().open()
        }

    }

    override fun render(p_230430_1_: MatrixStack, p_230430_2_: Int, p_230430_3_: Int, p_230430_4_: Float) {
        renderBackground(p_230430_1_)
        super.render(p_230430_1_, p_230430_2_, p_230430_3_, p_230430_4_)
    }

    fun getWidgetAt(mouseX: Double, mouseY: Double): ProcedureWidget? {
        for(widget in editorZone.widgets) {
            if(widget is ProcedureWidget && widget.canConnect(mouseX, mouseY)) {
                return widget
            }
        }
        return null
    }

    override fun mouseClicked(p_231044_1_: Double, p_231044_3_: Double, p_231044_5_: Int): Boolean {
        if(p_231044_5_ == 2) {
            TestProcedureWidget(100, 100, 100, 20) parent editorZone
        }
        return super.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_)
    }
}
