package ru.hollowhorizon.hollowengine.client.lang

import de.fabmax.kool.modules.ui2.MutableStateValue
import de.fabmax.kool.modules.ui2.mutableStateListOf
import de.fabmax.kool.modules.ui2.mutableStateOf
import kotlinx.serialization.json.*
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.common.utils.json.JsonFormat
import java.io.File
import java.nio.charset.StandardCharsets

data class TranslationRow(
    val key: String,
    val sourceValue: String,
    var targetValue: MutableStateValue<String>,
    val originMod: String,
)

class LanguageViewModel {
    val allTranslations = linkedSetOf<TranslationRow>()
    val filteredTranslations = mutableStateListOf<TranslationRow>()

    val sourceLang = mutableStateOf("en_us")
    val targetLang = mutableStateOf("ru_ru")
    val searchQuery = mutableStateOf("")
    val showOnlyMissing = mutableStateOf(false)

    fun getAvailableLanguages(): List<String> {
        return Minecraft.getInstance().languageManager.languages.keys.sorted()
    }

    fun load() {
        allTranslations.clear()

        val sourceMap = loadLanguageMap(sourceLang.value)
        val targetMap = loadLanguageMap(targetLang.value)
        val customTargetMap = loadCustomTranslations(targetLang.value)

        val allKeys = (sourceMap.keys + targetMap.keys + customTargetMap.keys).distinct()

        allKeys.forEach { key ->
            if (key.isEmpty()) return@forEach
            val sourceVal = sourceMap[key] ?: ""
            val targetVal = customTargetMap[key] ?: targetMap[key] ?: ""
            allTranslations.add(TranslationRow(key, sourceVal, mutableStateOf(targetVal), "Mod"))
        }

        applyFilters()
    }

    private fun loadLanguageMap(langCode: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val manager = Minecraft.getInstance().resourceManager

        // TODO: Вероятно стоит объединять все ключи из разных языков, даже если в искомом их нет.
        //  Либо стоит присмотреться к классу ClientLanguage, там уже есть готовый HashMap, но вероятно там не все ключи
        manager.listResources("lang") { it.path.endsWith("$langCode.json") }
            .forEach { (_, resource) ->
                try {
                    val json = Json.parseToJsonElement(resource.open().bufferedReader().readText())
                    json.jsonObject.forEach { (k, v) ->
                        map[k] = v.jsonPrimitive.content
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        return map
    }

    private fun loadCustomTranslations(langCode: String): Map<String, String> {
        val file = File(Minecraft.getInstance().gameDirectory, "hollowengine/assets/hollowengine/lang/$langCode.json")
        if (!file.exists()) return emptyMap()

        return try {
            val json = Json.parseToJsonElement(file.readText())
            json.jsonObject.mapValues { it.value.jsonPrimitive.content }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun applyFilters() {
        filteredTranslations.clear()
        val query = searchQuery.value.lowercase()

        filteredTranslations.addAll(allTranslations.filter {
            val matchesSearch = it.key.lowercase().contains(query) || it.sourceValue.lowercase().contains(query)
            val matchesMissing = if (showOnlyMissing.value) it.targetValue.value.isEmpty() else true
            matchesSearch && matchesMissing
        })
    }

    fun save() {
        val langCode = targetLang.value
        val folder = File(Minecraft.getInstance().gameDirectory, "hollowengine/assets/hollowengine/lang/")
        folder.mkdirs()

        val file = File(folder, "$langCode.json")
        val jsonMap = allTranslations
            .filter { it.targetValue.value.isNotEmpty() }
            .associate { it.key to JsonPrimitive(it.targetValue.value) }

        val jsonText = JsonFormat.encodeToString(JsonObject(jsonMap))
        file.writeText(jsonText, StandardCharsets.UTF_8)

        // TODO: Стоит фильтровать изменённый перевод и тот, что был изначально
    }
}