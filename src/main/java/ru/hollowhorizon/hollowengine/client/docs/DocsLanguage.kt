package ru.hollowhorizon.hollowengine.client.docs

import com.google.common.collect.ImmutableMap
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.language.ClientLanguage
import net.minecraft.client.resources.language.LanguageInfo
import net.minecraft.locale.Language
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterReloadListenersEvent
import java.io.IOException

class DocsLanguage(storage: Map<String, String>, defaultRightToLeft: Boolean) :
    ClientLanguage(storage, defaultRightToLeft) {
    companion object {
        internal lateinit var INSTANCE: DocsLanguage

        //? if >=1.20.1 {
        private val DEFAULT_LANGUAGE = LanguageInfo("US", "English", false)
        //?} else {
        /*private val DEFAULT_LANGUAGE = LanguageInfo("US", "English", "en_us", false)

        *///?}
        fun load(manager: ResourceManager): DocsLanguage {
            val original = Minecraft.getInstance().languageManager

            val fileNames = ArrayList<String>(2)
            //? if >=1.20.1 {
            var bl = DEFAULT_LANGUAGE.bidirectional()
            //?} else {
            /*var bl = DEFAULT_LANGUAGE.isBidirectional
            *///?}
            fileNames.add("en_us")
            if (
                //? if >=1.20.1 {
                original.selected != "en_us"
                //?} else {
                /*original.selected.name != "en_us"
                *///?}
            ) {
                //? if >=1.20.1 {
                val languageInfo = original.languages[original.selected]
                if (languageInfo != null) {
                    fileNames.add(original.selected)
                    bl = languageInfo.bidirectional()
                }
                //?} else {
                /*val languageInfo = original.selected
                fileNames.add(original.selected.name)
                bl = languageInfo.isBidirectional
                *///?}
            }

            val map = mutableMapOf<String, String>()

            for (fileName in fileNames) {
                val path = "lang/docs/$fileName.json"

                for (namespaces in manager.namespaces) {
                    try {
                        val resourceLocation = "$namespaces:$path".rl
                        appendLang(fileName, manager.getResourceStack(resourceLocation), map)
                    } catch (var10: Exception) {
                        HollowCore.LOGGER.warn(
                            "Skipped language file: {}:{} ({})", *arrayOf<Any>(namespaces, path, var10.toString())
                        )
                    }
                }
            }

            return DocsLanguage(ImmutableMap.copyOf(map), bl)
        }

        fun getInstance() = INSTANCE
    }
}

@SubscribeEvent
fun onClientReload(event: RegisterReloadListenersEvent.Client) {
    event.register(ResourceManagerReloadListener { manager ->
        DocsLanguage.INSTANCE = DocsLanguage.load(manager)
    })
}

internal fun appendLang(languageName: String, resources: List<Resource>, destinationMap: MutableMap<String, String>) {

    for (resource in resources) {
        try {
            val inputStream = resource.open()

            try {
                Language.loadFromJson(inputStream) { key: String, value: String ->
                    destinationMap[key] = value
                }
            } catch (ex: Throwable) {
                try {
                    inputStream.close()
                } catch (strEx: Throwable) {
                    ex.addSuppressed(strEx)
                }

                throw ex
            }

            inputStream.close()
        } catch (var10: IOException) {
            HollowCore.LOGGER.warn(
                "Failed to load translations for {} from pack {}",
                *arrayOf<Any>(languageName, resource.sourcePackId(), var10)
            )
        }
    }
}