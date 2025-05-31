package ru.hollowhorizon.hollowengine.ecs.npc

import de.fabmax.kool.modules.ui2.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.nbt.Tag
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapability
import ru.hollowhorizon.hc.common.coroutines.coroutineScope
import ru.hollowhorizon.hc.common.coroutines.scopeSync
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.server.ServerChatEvent
import ru.hollowhorizon.hc.common.utils.nbt.ForUuid
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.ai.ContentItem
import ru.hollowhorizon.hollowengine.common.ai.Message
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.inline.InlineScript
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.say
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.player.send
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions
import ru.hollowhorizon.hollowengine.ecs.RegisterComponent
import java.util.*
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.valueOrNull

@Serializable
abstract class NpcComponent : Composable {
    @Transient
    lateinit var npc: NpcEntity

    open fun onInteract(player: Player, hand: InteractionHand) {}
    open fun canPickup(itemEntity: ItemEntity): Boolean = false
    open fun tick() {}
    open fun onDeath(damageSource: DamageSource) {}
}

@HollowCapability(NpcEntity::class)
class NpcComponentsCapability : CapabilityInstance() {
    val components by syncableList<NpcComponent>()

    override fun canAcceptFromClient(player: Player, tag: Tag): Boolean {
        return player.hasPermissions(PlayerPermissions.GAMEMASTER)
    }
}

@RegisterComponent("utils/greetings")
@Serializable
class GreetingComponent : NpcComponent() {
    private var message = "Привет!"

    override fun onInteract(player: Player, hand: InteractionHand) {
        npc say message
    }

    override fun UiScope.compose() {
        Row(Grow.Std) {
            Text("Сообщение: ") {}
            TextField {
                modifier.text(message)
                    .onChange { message = it }
                    .width(Grow.Std)
            }
        }
    }
}

@RegisterComponent("actions/look")
@Serializable
class LookComponent : NpcComponent() {
    var target: @Serializable(ForUuid::class) UUID? = null

    override fun tick() {
        npc.level().getPlayerByUUID(target ?: return)?.let {
            npc.lookControl.setLookAt(it)
        }
    }

    override fun UiScope.compose() {
        val players = (Minecraft.getInstance().player?.connection?.listedOnlinePlayers ?: emptySet()).toList()
        if (target == null) target = players.map { it.profile.id }.firstOrNull()

        Row(Grow.Std) {
            Text("Игрок: ") {}
            ComboBox {
                modifier.items(players.map { it.tabListDisplayName?.string ?: it.profile.name })
                    .selectedIndex(players.indexOfFirst { it.profile.id == target }.coerceAtLeast(0))
                    .onItemSelected {
                        target = players[it].profile.id
                    }
                    .padding(sizes.smallGap * 0.5f)
                    .margin(horizontal = sizes.smallGap)
                    .width(Grow.Std)
            }
        }
    }
}

@RegisterComponent("actions/move")
@Serializable
class MoveComponent : NpcComponent() {
    var target: @Serializable(ForUuid::class) UUID? = null
    var distance: Double = 0.5

    override fun tick() {
        npc.level().getPlayerByUUID(target ?: return)?.let {
            if (npc.distanceTo(it) > distance) npc.navigation.moveTo(it, 1.0)
            else npc.navigation.stop()
        }
    }

    override fun UiScope.compose() {
        val players = (Minecraft.getInstance().player?.connection?.listedOnlinePlayers ?: emptySet()).toList()
        if (target == null) target = players.map { it.profile.id }.firstOrNull()

        Row(Grow.Std) {
            Text("Игрок: ") {}
            ComboBox {
                modifier.items(players.map { it.tabListDisplayName?.string ?: it.profile.name })
                    .selectedIndex(players.indexOfFirst { it.profile.id == target }.coerceAtLeast(0))
                    .onItemSelected {
                        target = players[it].profile.id
                    }
                    .padding(sizes.smallGap * 0.5f)
                    .margin(horizontal = sizes.smallGap)
                    .width(Grow.Std)
            }
        }
        Row(Grow.Std) {
            Text("Расстояние остановки: ") {}
            var tempText by remember(distance.toString())

            TextField {
                modifier.width(Grow.Std)
                    .text(tempText)
                    .onChange {
                        tempText = it

                        it.toDoubleOrNull()?.let { c ->
                            distance = c
                        } ?: run {
                            distance = 0.0
                        }
                    }
                    .onEnterPressed {
                        scopeSync {
                            val result = ScriptingCompiler.compileText<InlineScript>(tempText)
                                .execute()
                            result.valueOrNull()?.let {
                                (it.returnValue as? ResultValue.Value)?.let {
                                    (it.value as? Number)?.let {
                                        distance = it.toDouble()
                                        tempText = distance.toString()
                                    }
                                }
                            }
                        }
                    }
                    .alignY(AlignmentY.Center)
            }
        }
    }
}

@RegisterComponent("ai/shapesinc")
@Serializable
class ShapeComponent : NpcComponent() {
    var model = ""

    override fun UiScope.compose() {
        Row(Grow.Std) {
            Text("AI Model: ") {}
            TextField {
                modifier.text(model)
                    .onChange { model = it }
                    .width(Grow.Std)
            }
        }
    }
}

val messages = HashMap<NpcEntity, MutableList<Message>>()

@SubscribeEvent
fun onChat(event: ServerChatEvent) {
    val text = event.message.string

    event.player.level().getEntitiesOfClass(NpcEntity::class.java, event.player.boundingBox.inflate(35.0))
        .forEach { npc ->
            npc.components.filterIsInstance<ShapeComponent>().forEach {
                event.player.server.coroutineScope.launch {
                    val message = Message("user", text)
                    val chat = messages.getOrPut(npc) { arrayListOf() }
                    chat.add(message)

                    val result =
                        HollowEngine.shapesApi.chatCompletions(it.model, chat)
                    val text = result.content as ContentItem.Text
                    event.player send "[${npc.name}] ${text.value}"
                }
            }
        }
}