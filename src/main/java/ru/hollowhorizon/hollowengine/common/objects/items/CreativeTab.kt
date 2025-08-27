package ru.hollowhorizon.hollowengine.common.objects.items

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.item.BuildTabContentsEvent

interface CreativeTab {
    fun tab(): CreativeModeTab
}

@SubscribeEvent
fun onBuildContent(event: BuildTabContentsEvent) {
    BuiltInRegistries.ITEM.asSequence().filterIsInstance<CreativeTab>().filter { it.tab() == event.tab }
        .forEach { event.accept(it as Item) }
}