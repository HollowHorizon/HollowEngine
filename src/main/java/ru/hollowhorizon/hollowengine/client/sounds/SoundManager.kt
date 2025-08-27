package ru.hollowhorizon.hollowengine.client.sounds

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.audio.SoundBuffer
import ru.hollowhorizon.hollowengine.client.audio.Wave
import ru.hollowhorizon.hollowengine.client.audio.formats.Mp3Format
import ru.hollowhorizon.hollowengine.client.audio.formats.OggFormat
import ru.hollowhorizon.hollowengine.client.audio.formats.WavFormat

object SoundManager : ResourceManagerReloadListener {
    private val SUPPORTED_FORMATS = arrayOf("ogg", "mp3", "wav")
    private val SOUNDS = hashMapOf<ResourceLocation, SoundBuffer>()

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        SOUNDS.forEach { it.value.delete() }
        SOUNDS.clear()
        SOUNDS += resourceManager.listResources("audio") { it.path.substringAfterLast('.') in SUPPORTED_FORMATS }
            .mapNotNull { (location, resource) ->
                try {
                    location to loadSound(resourceManager, location)
                } catch (e: Exception) {
                    HollowCore.LOGGER.warn("Error while loading $location", e)
                    null
                }
            }
    }

    private fun loadSound(manager: ResourceManager, location: ResourceLocation): SoundBuffer {
        manager.getResource(location).orElseThrow().open().use { stream ->
            return when (location.path.substringAfterLast('.')) {
                "mp3" -> Mp3Format.read(stream)
                "wav" -> WavFormat.read(stream)
                "ogg" -> OggFormat.read(stream)
                else -> error("Unsupported audio format $location")
            }.let(::SoundBuffer)
        }
    }

    fun getSound(location: ResourceLocation): SoundBuffer = SOUNDS.getOrPut(location) {
        loadSound(Minecraft.getInstance().resourceManager, location)
    }

    fun getSoundOrNull(location: ResourceLocation): SoundBuffer? = SOUNDS[location]

    operator fun contains(location: ResourceLocation): Boolean = SOUNDS.containsKey(location)

    val allSounds: Map<ResourceLocation, SoundBuffer>
        get() = SOUNDS
}