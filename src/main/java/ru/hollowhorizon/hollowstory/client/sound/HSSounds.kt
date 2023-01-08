package ru.hollowhorizon.hollowstory.client.sound

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
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

        val sounds = arrayOf(
            "al1", "al2", "al3", "al4", "al5", "al6", "al7", "al8", "al9", "al10", "al11", "al12", "al13", "al14",
            "al15", "al16", "slider_button",
            "ch1", "ch2",
            "cr1", "cr2", "cr3", "cr4", "cr5", "cr6",
            "fr1", "fr2", "fr3", "fr4", "fr5",
            "ma1", "ma2", "ma3", "ma4", "ma5", "ma6", "ma7", "ma8", "ma9",
            "ma10", "ma11", "ma12", "ma13", "ma14", "ma15", "ma16", "ma17", "ma18",
        )

        for (sound in sounds) {
            registerer.register(sound) { HollowSoundEvent(HollowStory.MODID, sound) }
        }

        registerer.register(FMLJavaModLoadingContext.get().modEventBus)
    }
}