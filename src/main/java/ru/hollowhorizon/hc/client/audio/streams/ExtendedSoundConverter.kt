package ru.hollowhorizon.hc.client.audio.streams

import net.minecraft.resources.FileToIdConverter
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import ru.hollowhorizon.hc.common.utils.rl

object ExtendedSoundConverter : FileToIdConverter("sounds", ".ogg") {
    override fun idToFile(id: ResourceLocation): ResourceLocation {
        if (id.path.let { it.endsWith(".mp3") || it.endsWith(".wav") || it.endsWith(".ogg") }) {
            return id.withPath("sounds/" + id.path)
        }
        return super.idToFile(id)
    }

    override fun fileToId(file: ResourceLocation): ResourceLocation {
        if (file.path.let { it.endsWith(".mp3") || it.endsWith(".wav") || it.endsWith(".ogg") }) {
            return "${file.namespace}:${file.path.substringAfter("sounds/")}".rl
        }
        return super.fileToId(file)
    }

    override fun listMatchingResources(resourceManager: ResourceManager): MutableMap<ResourceLocation, Resource> {
        return resourceManager.listResources(
            "sounds"
        ) { it.path.let { it.endsWith(".ogg") || it.endsWith(".mp3") || it.endsWith(".wav") } }
    }

    override fun listMatchingResourceStacks(resourceManager: ResourceManager): MutableMap<ResourceLocation, MutableList<Resource>> {
        return resourceManager.listResourceStacks(
            "sounds"
        ) { it.path.let { it.endsWith(".ogg") || it.endsWith(".mp3") || it.endsWith(".wav") } }
    }
}