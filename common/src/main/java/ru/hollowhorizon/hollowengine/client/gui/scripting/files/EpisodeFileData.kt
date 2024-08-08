package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import imgui.type.ImBoolean
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hollowengine.common.story.episode.Episode
import ru.hollowhorizon.hollowengine.common.story.episode.EpisodesCapability

class EpisodeFileData(name: String, path: String, open: ImBoolean, var episode: Episode) : FileData(name, path, open) {
    override fun draw() {
        episode.edit()
    }

    override fun save() {
        val level = Minecraft.getInstance().level ?: return

        level[EpisodesCapability::class].episodes[path] = episode
    }
}