package ru.hollowhorizon.hollowengine.common.scripting.story

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.nbt.ForUuid
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowengine.common.npcs.ICharacter
import java.util.*

private val Player.scriptName: String?
    get() = if (this.persistentData.contains("hs_name")) this.persistentData.getString("hs_name") else null

