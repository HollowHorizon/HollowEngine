package ru.hollowhorizon.hollowengine.common.coroutines

import kotlinx.coroutines.CoroutineScope
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.attachments.api.AttachmentRegistry

val Entity.coroutineScope: CoroutineScope get() = AttachmentRegistry.coroutineScope(this)
