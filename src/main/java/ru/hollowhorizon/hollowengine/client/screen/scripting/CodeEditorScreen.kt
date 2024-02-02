package ru.hollowhorizon.hollowengine.client.screen.scripting

import net.minecraft.client.gui.components.MultiLineEditBox
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.screens.widget.button.BaseButton
import ru.hollowhorizon.hc.client.screens.widget.layout.PlacementType
import ru.hollowhorizon.hc.client.screens.widget.layout.box
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.rl

class CodeEditorScreen : HollowScreen() {
    private var editBox: MultiLineEditBox? = null

    override fun init() {
        super.init()

        box {
            placementType = PlacementType.GRID

            elements {
                editBox = +HighlightEditBox(90.pc.w().value, 80.pc.h().value)

                +BaseButton(
                    0, 0, 43.pc.w().value, 20,
                    "hollowengine.save".mcTranslate,
                    { onClose() },
                    "hollowengine:textures/gui/long_button.png".rl
                )
                +BaseButton(
                    0, 0, 43.pc.w().value, 20,
                    "hollowengine.cancel".mcTranslate,
                    { onClose() },
                    "hollowengine:textures/gui/long_button.png".rl
                )
            }
        }

    }

    override fun tick() {
        super.tick()
        editBox?.tick()
    }
}