package ru.hollowhorizon.hollowengine.common.scripting.types

import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.common.components.Component

open class ServerComponent(owner: MinecraftServer) : Component<MinecraftServer>(owner)
open class LivingEntityComponent(owner: LivingEntity) : Component<LivingEntity>(owner)