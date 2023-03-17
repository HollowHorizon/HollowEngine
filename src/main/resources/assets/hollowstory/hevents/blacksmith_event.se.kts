package assets.hollowstory.hevents

import net.minecraft.client.Minecraft
import net.minecraft.command.arguments.EntityAnchorArgument
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraft.world.GameType
import net.minecraft.world.server.ServerWorld
import net.minecraftforge.fml.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.client.utils.toRL
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowstory.client.screen.OverlayScreen
import ru.hollowhorizon.hollowstory.common.entities.NPCEntity
import ru.hollowhorizon.hollowstory.common.npcs.NPCSettings
import ru.hollowhorizon.hollowstory.cutscenes.replay.Replay
import ru.hollowhorizon.hollowstory.cutscenes.replay.ReplayPlayer

val level: ServerWorld = ServerLifecycleHooks.getCurrentServer().overworld()
val player: PlayerEntity = team.getAllOnline().first().mcPlayer!!

val startPos = player.position()
val overlay = OverlayScreen()

Minecraft.getInstance().gui.setTitles("Тем временем в деревне...".toSTC(), null, 20, 30, 20)

wait(2f)

Minecraft.getInstance().setScreen(overlay)
overlay.makeBlack(1.5f)

wait(1.5f)
player.setGameMode(GameType.SPECTATOR)
(player as ServerPlayerEntity).teleportTo(level, 219.2, 72.0, -790.3, -167.0f, 5.4f)

val obbik = NPCEntity(
    NPCSettings(
        name = "Оббик",
        puppetEntity = "minecraft:villager@{VillagerData:{profession:armorer,level:2,type:savanna}}",
    ),
    level
)
obbik.moveTo(215.5, 71.1, -797.5, 180.0f, 30f)
obbik.lookAt(EntityAnchorArgument.Type.EYES, player.position())
level.addFreshEntity(obbik)

val obbikPlayer = ReplayPlayer(obbik).apply {
    isLooped = false
    saveEntity = false
    applyWorldChanges = true
}

obbikPlayer.play(level, Replay.fromResourceLocation("hollowstory:replays/obbik.hc".toRL()))

wait(1.5f)

val kay = NPCEntity(
    NPCSettings(
        name = "Кай",
        puppetEntity = "minecraft:villager@{VillagerData:{profession:weaponsmith,level:2,type:savanna}}",
    ),
    level
)
kay.moveTo(220.5, 72.1, -791.5, 180.0f, 30f)
kay.lookAt(EntityAnchorArgument.Type.EYES, player.position())
level.addFreshEntity(kay)

overlay.makeTransparent(1f)

val kayPlayer = ReplayPlayer(kay).apply {
    isLooped = false
    saveEntity = false
    applyWorldChanges = true
}

kayPlayer.play(level, Replay.fromResourceLocation("hollowstory:replays/kay.hc".toRL()))

play("hollowstory:ob1")
team.sendMessage("<Оббик> Не отвлекаю? Как там наш заказ?")

wait(3.5f)

play("hollowstory:ka1")
team.sendMessage("<Кай> О! Это ты! Нам осталось не так уж и много. Всё будет в лучшем виде!")

wait(5f)

play("hollowstory:ob2")
team.sendMessage("<Оббик> Я и не сомневаюсь")

wait(3f)

play("hollowstory:ob3")
team.sendMessage("<Оббик> Хочу узнать, что ты думаешь насчёт нашего гостя, у кого не спрошу, у всех своё на уме, а парень то вроде хороший...")

wait(6f)

play("hollowstory:ka2")
team.sendMessage("<Кай> Хмм...")

wait(2f)

play("hollowstory:ka3")
team.sendMessage("<Кай> ты проходи, чего у порога стоять?")

wait(1f)
overlay.makeBlack(2f)
wait(2f)

(player as ServerPlayerEntity).teleportTo(level, 218.4, 72.0, -788.7, -5.0f, 5.4f)

overlay.makeTransparent(2f)
wait(4f)

play("hollowstory:ka4")
team.sendMessage("<Кай> Ничего не думаю, понимаешь? Сколько у нас проезжих гостей было? Коли хороший, пусть остаётся, сколько захочет, а если нет, его быстро зашиворот выгонят. Пока понаблюдаем, а там будет уже видно будет.")

wait(12f)

overlay.makeBlack(2f)
wait(2f)

(player as ServerPlayerEntity).teleportTo(level, startPos.x(), startPos.y(), startPos.z(), 0f, 0f)
player.setGameMode(GameType.SURVIVAL)

overlay.makeTransparent(2f)
wait(2f)

Minecraft.getInstance().setScreen(null)