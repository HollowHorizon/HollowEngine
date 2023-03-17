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

        for (i in 1..4) sounds += "al$i"
        for (i in 1..4) sounds += "am$i"
        for (i in 1..6) sounds += "ar$i"
        for (i in 1..11) sounds += "cin$i"
        for (i in 1..10) sounds += "fr$i"
        for (i in 1..2) sounds += "lo$i"
        for (i in 1..3) sounds += "meteor$i"
        sounds+="mk"
        for (i in 1..18) sounds += "nik$i"
        sounds+="ob1"
        sounds+="tolpa"
        for (i in 1..7) sounds += "tom$i"
        sounds+="ub"
        sounds += arrayOf("kai1", "kai2", "kai4")

        for (sound in sounds) {
            registerer.register(sound) { HollowSoundEvent(HollowStory.MODID, sound) }
        }

        registerer.register(FMLJavaModLoadingContext.get().modEventBus)
    }
}