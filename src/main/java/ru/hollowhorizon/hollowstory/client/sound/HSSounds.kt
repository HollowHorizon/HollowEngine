package ru.hollowhorizon.hollowstory.client.sound

import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.loading.FMLPaths
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.sounds.HollowSoundEvent
import ru.hollowhorizon.hollowstory.HollowStory

object HSSounds {
    @JvmField
    val SLIDER_BUTTON = HollowSoundEvent(HollowStory.MODID, "slider_button")

    @JvmStatic
    fun init() {
        val registerer = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HollowStory.MODID)

        val sounds = arrayListOf<String>()

        sounds += "button_0"

        for (i in 1..6) sounds += "al$i"
        for (i in 1..22) sounds += "am$i"
        for (i in 1..7) sounds += "ar$i"
        for (i in 1..12) sounds += "tom$i"

        for (sound in sounds) {
            registerer.register(sound) { HollowSoundEvent(HollowStory.MODID, sound) }
        }

        registerer.register(FMLJavaModLoadingContext.get().modEventBus)
    }
}