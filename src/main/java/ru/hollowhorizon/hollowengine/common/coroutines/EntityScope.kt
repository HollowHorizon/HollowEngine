package ru.hollowhorizon.hollowengine.common.coroutines

import kotlinx.coroutines.SupervisorJob
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import kotlin.coroutines.CoroutineContext

class EntityScope(
    override val coroutineContext: CoroutineContext,
    onDirty: (() -> Unit)? = null,
) : OwnerScope(coroutineContext, onDirty) {
    constructor(entity: Entity) : this(SupervisorJob() + (entity.server?.dispatcher ?: Minecraft.getInstance().dispatcher))
}
