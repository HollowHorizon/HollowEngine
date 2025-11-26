//? if fabric {
/*package ru.hollowhorizon.hollowengine.fabric.internal

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.PreparableReloadListener
import ru.hollowhorizon.hollowengine.common.utils.rl

class DelegatedReloadListener(private val eventListener: PreparableReloadListener) :
    PreparableReloadListener by eventListener, IdentifiableResourceReloadListener {
    override fun getFabricId(): ResourceLocation {
        return "hollowengine_generated:${eventListener.javaClass.name.lowercase().replace("$", ".")}".rl
    }

    override fun getName(): String? {
        return eventListener.name
    }
}
*///?}