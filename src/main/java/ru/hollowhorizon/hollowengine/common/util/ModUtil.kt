package ru.hollowhorizon.hollowengine.common.util

import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.moddiscovery.ModInfo
import ru.hollowhorizon.hollowengine.HollowEngine

object ModUtil {
    fun updateModNames() {
        val optionalMod = ModList.get().getModContainerById(HollowEngine.MODID)

        if (!optionalMod.isPresent) return

        val mod = optionalMod.get().modInfo

        if (mod.displayName != "Hollow Engine") {
            val displayNameSetter = ModInfo::class.java.getDeclaredField("displayName")

            displayNameSetter.isAccessible = true
            displayNameSetter[mod] = "Hollow Engine"
        }
    }
}