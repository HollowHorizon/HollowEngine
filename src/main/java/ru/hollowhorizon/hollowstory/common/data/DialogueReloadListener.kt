package ru.hollowhorizon.hollowstory.common.data

import net.minecraft.client.resources.ReloadListener
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.profiler.IProfiler
import net.minecraft.resources.IResourceManager
import net.minecraftforge.event.OnDatapackSyncEvent
import net.minecraftforge.fml.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.api.registy.HollowPacket
import ru.hollowhorizon.hc.client.utils.HollowNBTSerializer
import ru.hollowhorizon.hc.client.utils.NBTUtils
import ru.hollowhorizon.hc.common.network.UniversalPacket
import ru.hollowhorizon.hollowstory.common.hollowscript.dialogues.DialogueStorage

class DialogueReloadListener : ReloadListener<Map<String, String>>() {
    override fun prepare(resourceManager: IResourceManager, profiler: IProfiler): Map<String, String> {
        val dialogues = resourceManager.listResources("dialogues") { file -> file.endsWith(".hsd.kts") }

        val map = mutableMapOf<String, String>()
        dialogues.forEach { dialogue ->
            map["${dialogue.namespace}/${dialogue.path.substring(10, dialogue.path.length - ".hsd.kts".length)}".replace(
                "/", "."
            )] = resourceManager.getResource(dialogue).inputStream.reader().readText()
        }

        return map
    }

    override fun apply(map: Map<String, String>, manager: IResourceManager, profiler: IProfiler) {
        DialogueStorage.DIALOGUES.clear()
        DialogueStorage.DIALOGUES.putAll(map)
    }
}