package ru.hollowhorizon.hollowengine.common.docs

import com.google.common.collect.ImmutableMap
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.language.ClientLanguage
import net.minecraft.client.resources.language.LanguageInfo
import net.minecraft.locale.Language
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterReloadListenersEvent
import java.io.IOException

class DocsLanguage(storage: Map<String, String>, defaultRightToLeft: Boolean) :
    ClientLanguage(storage, defaultRightToLeft) {
    companion object {
        internal lateinit var INSTANCE: DocsLanguage
        private val DEFAULT_LANGUAGE = LanguageInfo("US", "English", false)

        fun load(manager: ResourceManager): DocsLanguage {
            val original = Minecraft.getInstance().languageManager

            val fileNames = ArrayList<String>(2)
            var bl = DEFAULT_LANGUAGE.bidirectional()
            fileNames.add("en_us")
            if (original.selected != "en_us") {
                val languageInfo = original.languages[original.selected]
                if (languageInfo != null) {
                    fileNames.add(original.selected)
                    bl = languageInfo.bidirectional()
                }
            }

            val map = mutableMapOf<String, String>()

            for (fileName in fileNames) {
                val string2 = "lang/docs/$fileName.json"

                for (string3 in manager.namespaces) {
                    try {
                        val resourceLocation = ResourceLocation.fromNamespaceAndPath(string3, string2)
                        appendLang(fileName, manager.getResourceStack(resourceLocation), map)
                    } catch (var10: Exception) {
                        HollowCore.LOGGER.warn(
                            "Skipped language file: {}:{} ({})", *arrayOf<Any>(string3, string2, var10.toString())
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