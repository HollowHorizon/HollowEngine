package ru.hollowhorizon.hollowengine.client.screen.npcs

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.nbt.Tag
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.client.models.gltf.manager.RawPose
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.common.ui.Alignment
import ru.hollowhorizon.hc.common.ui.Anchor
import ru.hollowhorizon.hc.client.screens.widget.button.BaseButton
import ru.hollowhorizon.hc.client.screens.widget.layout.PlacementType
import ru.hollowhorizon.hc.client.screens.widget.layout.box
import ru.hollowhorizon.hc.client.utils.drawScaled
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.nbt.ForTag
import ru.hollowhorizon.hc.client.utils.nbt.save
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.client.screen.widget.HollowTextFieldWidget
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager

class SavePoseScreen(val path: RawPose) : HollowScreen() {

    init {
        Minecraft.getInstance().keyboardHandler.setSendRepeatsToGui(true)
    }

    override fun init() {
        super.init()

        box {
            size = 90.pc x 90.pc
            renderer = { stack, x, y, w, h ->
                fill(stack, x, y, x + w, y + h, 0x8800173D.toInt())
                fill(stack, x, y, x + w, y + 2, 0xFF07BBDB.toInt())
                fill(stack, x, y + h - 2, x + w, y + h, 0xFF07BBDB.toInt())
                fill(stack, x, y, x + 2, y + h, 0xFF07BBDB.toInt())
                fill(stack, x + w - 2, y, x + w, y + h, 0xFF07BBDB.toInt())

                font.drawScaled(
                    stack,
                    Anchor.CENTER,
                    "Введите название позы:".mcText,
                    x + w / 2,
                    y + 15,
                    0xFFFFFF,
                    1.5f
                )
            }

            elements {
                align = Alignment.CENTER
                spacing = 4.pc x 2.pc
                placementType = PlacementType.GRID

                val field = +HollowTextFieldWidget(
                    font, 0, 0, 90.pc.w().value, 20,
                    "".mcText,
                    "hollowengine:textures/gui/text_field.png".rl
                )
                +BaseButton(
                    0, 0, 43.pc.w().value, 20,
                    "hollowengine.save".mcTranslate,
                    { SavePoseOnServerPacket(path.toNBT(), field.value).send(); onClose() },
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

    override fun onClose() {
        super.onClose()
        Minecraft.getInstance().keyboardHandler.setSendRepeatsToGui(false)
    }
}

@HollowPacketV2(HollowPacketV2.Direction.TO_SERVER)
@Serializable
class SavePoseOnServerPacket(private val pose: @Serializable(ForTag::class) Tag, private val fileName: String) :
    HollowPacketV3<SavePoseOnServerPacket> {
    override fun handle(player: Player, data: SavePoseOnServerPacket) {
        if (!player.hasPermissions(2)) return

        val file = DirectoryManager.HOLLOW_ENGINE.resolve("npcs/poses/$fileName.nbt")
        file.parentFile.mkdirs()

        data.pose.save(file.outputStream())
    }

}