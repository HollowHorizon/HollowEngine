package assets.hollowstory.hevents

import net.minecraft.block.Blocks
import net.minecraft.client.Minecraft
import net.minecraft.command.arguments.EntityAnchorArgument
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraft.util.math.vector.Vector3d
import net.minecraft.world.server.ServerWorld
import net.minecraftforge.common.ToolType
import net.minecraftforge.event.TickEvent.ServerTickEvent
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock
import ru.hollowhorizon.hc.client.utils.toRL
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowstory.client.gui.DialogueScreen
import ru.hollowhorizon.hollowstory.common.entities.NPCEntity
import ru.hollowhorizon.hollowstory.common.npcs.NPCSettings
import ru.hollowhorizon.hollowstory.cutscenes.replay.Replay
import ru.hollowhorizon.hollowstory.cutscenes.replay.ReplayPlayer
import ru.hollowhorizon.hollowstory.story.waitForgeEvent
import tallestegg.guardvillagers.GuardEntityType
import tallestegg.guardvillagers.entities.GuardEntity

val player: ServerPlayerEntity = team.getAllOnline().first().mcPlayer!! as ServerPlayerEntity
val level: ServerWorld = player.func_71121_q()

val villagePos = Vector3d(213.0, 73.0, -762.0)

//Просто ждём, пока игрок не придёт в деревню
waitForgeEvent<ServerTickEvent> { event ->
    var stop = false
    team.forAllOnline {
        if (it.mcPlayer!!.func_213303_ch().func_72438_d(villagePos) < 50) {
            stop = true
        }
    }
    return@waitForgeEvent stop
}

val guardPos = Vector3d(213.0, 72.5, -766.0)

val guard = GuardEntity(GuardEntityType.GUARD.get(), level)
guard.func_200203_b("Стражник".toSTC())
guard.func_174805_g(true)
guard.func_70012_b(guardPos.func_82615_a(), guardPos.func_82617_b(), guardPos.func_82616_c(), 0.0F, 0.0F)
level.func_217376_c(guard)
//guard.goalSelector.availableGoals.clear()

waitForgeEvent<LivingUpdateEvent> {
    if (!it.entity.equals(guard)) return@waitForgeEvent false
    (it.entity as GuardEntity).func_70661_as().func_75497_a(player, 0.5)
    return@waitForgeEvent it.entityLiving.func_213303_ch().func_72438_d(player.func_213303_ch()) < 5
}

wait(2f)

val waiter = Object()

Minecraft.func_71410_x().func_147108_a(DialogueScreen("hollowstory:dialogues/dialogue_2.hsd.kts".toRL()) {
    synchronized(waiter) {
        waiter.notify()
    }
})

synchronized(waiter) {
    waiter.wait()
}

//Фиг его знает, но почему-то у меня пару раз не сработала телепортация, поэтому я сделал так
player.func_70634_a(340.716, 71.100, -746.339)
player.func_70012_b(340.716, 71.100, -746.339, 54.8f, 3.8f)

val farmer = NPCEntity(
    NPCSettings(
        name = "Марко",
        puppetEntity = "minecraft:villager@{VillagerData:{profession:farmer,level:2,type:savanna}}",
    ),
    level
)
farmer.func_70012_b(337.9, 71.1, -742.5, -151.1f, 15f)
farmer.func_200602_a(EntityAnchorArgument.Type.EYES, player.func_213303_ch())
level.func_217376_c(farmer)


play("hollowstory:ma6")
team.sendMessage("<Марко> Смотри, это дом нашего картографа, а дорожка то совсем заросла. А теперь, повторяй за мной. Всё просто, как сделаешь, приду, чтобы проверить.")

val markoPlayer = ReplayPlayer(farmer).apply {
    isLooped = false
    saveEntity = true
    applyWorldChanges = true
}

markoPlayer.play(level, Replay.fromResourceLocation("hollowstory:replays/marko_1.hc".toRL()))

var count = 0

waitForgeEvent<RightClickBlock> {
    if (!it.side.isClient) return@waitForgeEvent false
    if (it.itemStack.toolTypes.contains(ToolType.SHOVEL) && level.func_180495_p(it.hitVec.func_216350_a())
            .func_203425_a(Blocks.field_196658_i)
    ) {
        Minecraft.func_71410_x().field_71456_v.func_175188_a("Вспахано земли: ${++count}".toSTC(), false)
    }
    return@waitForgeEvent count >= 50
}

waitForgeEvent<LivingUpdateEvent> {
    if (!it.entity.equals(farmer)) return@waitForgeEvent false
    (it.entity as NPCEntity).func_70661_as().func_75497_a(player, 1.0)
    return@waitForgeEvent it.entityLiving.func_213303_ch().func_72438_d(player.func_213303_ch()) < 5
}

wait(2f)

Minecraft.func_71410_x().func_147108_a(DialogueScreen("hollowstory:dialogues/dialogue_4.hsd.kts".toRL()) {
    player.func_70634_a(135.8, 66.1, -703.5)
    player.func_70012_b(135.8, 66.1, -703.5, 90.0f, 0.0f)

    farmer.func_70634_a(130.0, 66.3, -703.5)
    farmer.func_70012_b(130.0, 66.3, -703.5, -90.0f, 0.0f)
    farmer.func_184224_h(true)
})