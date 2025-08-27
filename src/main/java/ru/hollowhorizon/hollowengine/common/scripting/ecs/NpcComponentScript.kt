package ru.hollowhorizon.hollowengine.common.scripting.ecs

import ru.hollowhorizon.hollowengine.common.utils.ModList
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import ru.hollowhorizon.hollowengine.ecs.npc.NpcComponent
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.defaultImports

@KotlinScript(
    displayName = "Npc Component",
    fileExtension = "ncs.kts",
    compilationConfiguration = NpcComponentConfiguration::class
)
abstract class NpcComponentScript : NpcComponent()

class NpcComponentConfiguration : HollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hollowengine.common.capability.*",
        "net.minecraft.core.BlockPos",
        "net.minecraft.util.RandomSource",
        "net.minecraft.util.Mth",
        "net.minecraft.world.level.levelgen.Heightmap",
        "net.minecraft.world.phys.Vec3",
        "net.minecraft.world.entity.*",
        "net.minecraft.server.level.ServerLevel",
        "net.minecraft.world.damagesource.DamageSource",
        "net.minecraft.world.entity.player.Player",
        "ru.hollowhorizon.hollowengine.client.utils.*"
    )
    if (ModList.isLoaded("bbs")) defaultImports("ru.hollowhorizon.hollowengine.common.scripting.story.functions.bbs.*")
})