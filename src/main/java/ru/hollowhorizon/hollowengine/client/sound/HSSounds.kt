package ru.hollowhorizon.hollowengine.client.sound

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.sounds.HollowSoundEvent
import ru.hollowhorizon.hollowengine.HollowEngine

object HSSounds {
    @JvmField
    val SLIDER_BUTTON = HollowSoundEvent(HollowEngine.MODID, "button_0")

    @JvmStatic
    fun init() {
        val registerer = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HollowEngine.MODID)

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
            registerer.register(sound) { HollowSoundEvent(HollowEngine.MODID, sound) }
        }

        registerer.register(FMLJavaModLoadingContext.get().modEventBus)
    }
}