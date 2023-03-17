import ru.hollowhorizon.hollowstory.common.npcs.*

val npc: IHollowNPC = makeNPC(
    NPCSettings(
        name = "§7[Марко]",
        puppetEntity = "minecraft:villager@{VillagerData:{profession:farmer,level:2,type:savanna}}"
    ),
    randomPos()
)

npc.makeTask {
    movement.go(team) {
        speed = 0.9

        onTick = {
            look.at(team).async()
        }
    }.wait()
}