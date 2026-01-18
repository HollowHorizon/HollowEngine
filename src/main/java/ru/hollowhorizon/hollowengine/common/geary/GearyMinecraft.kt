package ru.hollowhorizon.hollowengine.common.geary

import com.mineinabyss.geary.addons.dsl.AddonSetup
import com.mineinabyss.geary.modules.Geary


inline fun AddonSetup<*>.onPluginEnable(crossinline run: Geary.() -> Unit) {
    onStart {
        gearyMinecraft.worldManager.global.run()
    }
}