package ru.hollowhorizon.hollowengine.client.gui.npcs

import de.fabmax.kool.Assets
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.MsdfFont
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.client.kool.Entity
import ru.hollowhorizon.hc.client.kool.KoolManager.MONOCRAFT_DATA
import ru.hollowhorizon.hc.client.kool.KoolScreen
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hc.common.network.HollowPacketHandler
import ru.hollowhorizon.hc.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity

class NPCMenuGui(val npc: NPCEntity) : KoolScreen() {
    override fun Scene.setup() {
        setupUiScene()

        val buttons = arrayListOf(
            "talk",
            "trade",
            "quests",
            "invite"
        )

        val sizes = Sizes.medium

        addPanelSurface(sizes = sizes.copy(normalText = MsdfFont(MONOCRAFT_DATA, 30f))) {
            modifier.background(null)
                .size(Grow.Std, Grow.Std)

            Image(remember {
                Texture2d {
                    Assets.loadImage2d("hollowengine:textures/gui/npc_menu/background.png").getOrThrow()
                }
            }) {
                modifier.size(Grow.Std, Grow.Std)
                    .imageSize(ImageSize.ZoomContent)

                LazyColumn(
                    width = Grow(0.4f),
                    height = Grow(0.9f),
                    containerModifier = {
                        it.background(null)
                            .align(AlignmentX.Start, AlignmentY.Center)
                            .margin(start = 35.dp, top = 70.dp, bottom = 70.dp)
                            .padding(35.dp)
                    }
                ) {
                    itemsIndexed(buttons) { index, item ->
                        val name = "hollowengine.npc.$item".lang

                        Row {
                            modifier.size(Grow.Std, Grow.Std)
                            modifier.alignX(AlignmentX.Center)

                            Image(
                                remember {
                                    Texture2d {
                                        Assets.loadImage2d("hollowengine:textures/gui/npc_menu/$item.png").getOrThrow()
                                    }
                                }
                            ) {
                                modifier.size(100.dp, 75.dp)
                                    .imageSize(ImageSize.ZoomContent)
                            }

                            Image(
                                remember {
                                    Texture2d {
                                        Assets.loadImage2d("hollowengine:textures/gui/npc_menu/button.png").getOrThrow()
                                    }
                                }
                            ) {
                                modifier.size(Grow.Std, Grow.Std)
                                    .imageSize(ImageSize.FitContent)
                                Text(name) {
                                    modifier.textAlign(AlignmentX.Center, AlignmentY.Center)
                                }
                            }

                            Image(
                                remember {
                                    Texture2d {
                                        Assets.loadImage2d("hollowengine:textures/gui/npc_menu/cursor.png").getOrThrow()
                                    }
                                }
                            ) {
                                modifier.size(67.dp, 75.dp)
                                    .imageSize(ImageSize.ZoomContent)
                            }
                        }

                    }
                }

                Entity(npc) {
                    modifier.size(Grow(0.45f), Grow(0.9f))
                        .align(AlignmentX.End, AlignmentY.Center)
                }
            }
        }
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
class NpcTalkPacket(val npcId: Int) : HollowPacket<NpcTalkPacket> {
    override fun handle(player: Player) {
        val npc = player.level().getEntity(npcId) as? NPCEntity ?: return

        PlayerTalkToNpcEvent(npc, player).post()
    }
}

class PlayerTalkToNpcEvent(val npc: NPCEntity, val player: Player) : Event