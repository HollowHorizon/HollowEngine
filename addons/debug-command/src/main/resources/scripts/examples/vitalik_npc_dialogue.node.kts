import kotlinx.coroutines.launch
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.data.data
import ru.hollowhorizon.hollowengine.common.data.dataKey
import ru.hollowhorizon.hollowengine.common.dialogue.DialogueCharacter
import ru.hollowhorizon.hollowengine.common.dialogue.DialogueController
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerInteractEvent
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.clearLookTarget
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.despawn
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.npc
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.player.send

val VISITS = dataKey<Int>("vitalik_dialogue_visits") { 0 }
val storyPath = path.removeSuffix(".node.kts") + ".story"

lateinit var vitalik: NpcEntity
var dialogueInProgress = false

onStart {
    val player = playerList.players.firstOrNull()
        ?: error("Для запуска примера в мире должен находиться хотя бы один игрок")

    val look = player.lookAngle
    val horizontalLook = Vec3(look.x, 0.0, look.z)
    val direction = if (horizontalLook.lengthSqr() > 1.0e-6) {
        horizontalLook.normalize()
    } else {
        Vec3(0.0, 0.0, 1.0)
    }

    vitalik = npc(
        pos = player.position().add(direction.scale(3.0)),
        name = "Виталик",
    )

    player send "Виталик появился перед тобой. Нажми по нему ПКМ, чтобы начать диалог."
}

PlayerInteractEvent.EntityInteract.subscribe(this) { event ->
    if (!::vitalik.isInitialized || event.target !== vitalik) return@subscribe
    if (event.hand != InteractionHand.MAIN_HAND || event.player.level().isClientSide) return@subscribe

    event.isCanceled = true
    val player = event.player as? ServerPlayer ?: return@subscribe

    if (dialogueInProgress) {
        player send "Виталик уже с кем-то разговаривает."
        return@subscribe
    }

    dialogueInProgress = true
    launch {
        try {
            val visits = vitalik.data.getOrPut(VISITS) + 1
            vitalik.data[VISITS] = visits

            DialogueController(storyPath).start(player) {
                character("Виталик", DialogueCharacter.of(vitalik))
                put("visits", visits)
            }
        } finally {
            dialogueInProgress = false
            if (::vitalik.isInitialized && vitalik.isAlive) {
                vitalik.clearLookTarget()
            }
        }
    }
}

onStop {
    if (::vitalik.isInitialized && !vitalik.isRemoved) {
        vitalik.despawn()
    }
}
