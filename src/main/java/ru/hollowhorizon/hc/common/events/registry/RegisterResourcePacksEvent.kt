package ru.hollowhorizon.hc.common.events.registry

import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.repository.Pack
import ru.hollowhorizon.hc.client.utils.asPack
import ru.hollowhorizon.hc.common.events.Event
import java.util.function.Consumer

class RegisterResourcePacksEvent(private val resourcePacks: Consumer<Pack>) : Event {
    fun addPack(pack: PackResources) {
        resourcePacks.accept(pack.asPack() ?: error("Resource packs not found!"))
    }
}