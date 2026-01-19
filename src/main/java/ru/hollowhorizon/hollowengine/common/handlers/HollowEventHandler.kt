package ru.hollowhorizon.hollowengine.common.handlers

import net.minecraft.client.gui.screens.Screen
import net.minecraft.locale.Language
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.ItemTooltipEvent
import ru.hollowhorizon.hollowengine.common.events.level.LevelEvent
import ru.hollowhorizon.hollowengine.common.events.server.ServerEvent
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent
import ru.hollowhorizon.hollowengine.common.utils.mcTranslate
import ru.hollowhorizon.hollowengine.common.utils.nbt.loadAsNBT
import ru.hollowhorizon.hollowengine.common.utils.nbt.save
import ru.hollowhorizon.hollowengine.mixins.DimensionDataStorageAccessor
import java.io.DataInputStream

object HollowEventHandler {

    @SubscribeEvent
    fun onTooltip(event: ItemTooltipEvent) {
        val desc = event.itemStack.item.descriptionId + ".hc_desc"
        val shiftDesc = event.itemStack.item.descriptionId + ".hc_shift_desc"
        val lang = Language.getInstance()

        if (lang.has(desc)) event.toolTip.add(desc.mcTranslate)

        if (Screen.hasShiftDown() && lang.has(shiftDesc)) event.toolTip.add(desc.mcTranslate)
    }

    //@SubscribeEvent
    fun onLevelSave(event: LevelEvent.Save) {
        val level = event.level as ServerLevel
        val folder = (level.chunkSource.dataStorage as DimensionDataStorageAccessor).dataFolder

        val tag = (event.level as ComponentDispatcher).container.save()
        if (tag.isEmpty && !event.level.server.isRunning) return
        val stream = folder.resolve("hollowengine-components.dat").outputStream()
        tag.save(stream)
        stream.close()
    }

    //@SubscribeEvent
    fun onLevelLoad(event: LevelEvent.Load) {
        val level = event.level as ServerLevel
        val folder = (level.chunkSource.dataStorage as DimensionDataStorageAccessor).dataFolder

        val components = folder.resolve("hollowengine-components.dat")
        if (components.exists()) {
            try {
                val tag = DataInputStream(components.inputStream()).loadAsNBT() as CompoundTag
                (event.level as ComponentDispatcher).container.load(tag)
            } catch (e: Exception) {
                HollowCore.LOGGER.warn(
                    "Exception, while loading components for level {}: ",
                    level.dimension().location(),
                    e
                )
            }
        }
    }

    //@SubscribeEvent
    fun onTickLevels(event: TickEvent.Server) {
        event.server.allLevels.forEach {
            (it as ComponentDispatcher).container.update()
        }
    }

    //@SubscribeEvent
    fun onTickClientLevel(event: TickEvent.Client) {
        (event.minecraft.level as? ComponentDispatcher)?.container?.update()
    }

    //@SubscribeEvent
    fun onServerStop(event: ServerEvent.Stoping) {
        event.server.allLevels.forEach {
            (it as ComponentDispatcher).apply {
                container.components.keys.forEach { container.detach(it) }
            }
        }
    }
}
