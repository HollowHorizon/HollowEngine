package ru.hollowhorizon.hollowengine.common.coroutines

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import net.minecraft.server.MinecraftServer

class ServerScope(
    server: MinecraftServer,
    onDirty: (() -> Unit)? = null,
) : OwnerScope(server.dispatcher + SupervisorJob(server.coroutineScope.coroutineContext[Job]), onDirty)
