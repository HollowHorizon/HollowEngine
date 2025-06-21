package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.level.LevelEvent
import ru.hollowhorizon.hc.common.utils.nbt.*
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager

const val PREFIX_MATCH_SCORE = 100
const val WORD_START_SCORE = 40
const val CAMEL_CASE_SCORE = 20
const val CONSECUTIVE_SCORE = 5
const val CASE_SENSITIVE_BONUS = 10
const val EXACT_MATCH_BONUS = 200
const val SHORT_NAME_BONUS = 30

data class MatchResult(val score: Int, val matchedIndices: List<Int>)

fun fuzzyCamelHumpScore(prefix: String, name: String): MatchResult? {
    if (prefix.isEmpty()) return MatchResult(0, emptyList())
    if (name.isEmpty()) return null

    val lowerPrefix = prefix
    val lowerName = name

    // Точное совпадение (высший приоритет)
    if (name == prefix) return MatchResult(1000 + EXACT_MATCH_BONUS + SHORT_NAME_BONUS, name.indices.toList())

    // Совпадение с начала (высокий приоритет)
    if (lowerName.startsWith(lowerPrefix)) {
        return MatchResult(1000 + if (name.startsWith(prefix)) CASE_SENSITIVE_BONUS else 0, prefix.indices.toList())
    }

    var score = 0
    val matchedIndices = mutableListOf<Int>()
    var prefixPos = 0
    var consecutive = 0
    var firstMatchIndex = -1
    var lastMatchIndex = -1

    for (i in name.indices) {
        val c = name[i]
        val isWordStart = when {
            i == 0 -> true
            c.isUpperCase() && name[i-1].isLowerCase() -> true
            c == '_' || c == '-' -> true
            else -> false
        }

        if (prefixPos < prefix.length && charMatches(c, prefix[prefixPos])) {
            matchedIndices.add(i)
            prefixPos++

            when {
                // Начало слова (высший приоритет)
                isWordStart -> {
                    score += WORD_START_SCORE
                    consecutive = WORD_START_SCORE / 2  // Бонус за последовательность
                }

                // CamelCase граница
                c.isUpperCase() -> {
                    score += CAMEL_CASE_SCORE
                    consecutive = CAMEL_CASE_SCORE / 2
                }

                // Обычное совпадение
                else -> {
                    score += 1
                    consecutive = maxOf(0, consecutive - 1)
                }
            }

            // Бонус за последовательные совпадения
            score += consecutive

            // Фиксируем позиции первого и последнего совпадения
            if (firstMatchIndex == -1) firstMatchIndex = i
            lastMatchIndex = i
        } else {
            consecutive = 0
        }
    }

    // Штраф за неполное совпадение
    if (prefixPos != prefix.length) return null

    // Бонусы/штрафы
    score += when {
        // Совпадение с начала слова
        firstMatchIndex == 0 -> PREFIX_MATCH_SCORE

        // Совпадение в начале слова (но не первого)
        name[firstMatchIndex].isUpperCase() -> PREFIX_MATCH_SCORE / 2

        else -> 0
    }

    // Штраф за расстояние от начала
    val positionPenalty = firstMatchIndex * 2
    score -= positionPenalty

    // Бонус за короткие имена
    if (name.length <= prefix.length + 3) {
        score += SHORT_NAME_BONUS
    }

    // Бонус за case-sensitive совпадение
    if (name.substring(firstMatchIndex, lastMatchIndex + 1).contains(prefix)) {
        score += CASE_SENSITIVE_BONUS
    }

    return MatchResult(score, matchedIndices)
}

private val enToRuLayout = ("qwertyuiop[]asdfghjkl;'zxcvbnm,./" zip "йцукенгшщзхъфывапролджэячсмитьбю.").toMap()
private val ruToEnLayout = enToRuLayout.map { it.value to it.key }.toMap()

private fun charMatches(inputChar: Char, targetChar: Char): Boolean {
    val input = inputChar
    val target = targetChar

    return when {
        input == target -> true
        enToRuLayout[input] == target -> true
        ruToEnLayout[input] == target -> true
        else -> false
    }
}

object UsageStatistics {
    private var stats = mutableMapOf<String, Int>()
    private val maxEntries = 10_000
    private val file = DirectoryManager.guiCache.resolve("statistics.ide")

    init {
        if (file.exists()) stats = NBTFormat.deserialize(file.inputStream().use { it.loadAsNBT() })
    }

    fun recordUsage(descriptor: DeclarationDescriptor) {
        val key = descriptor.stableKey()
        stats[key] = stats.getOrDefault(key, 0) + 1

        // Очистка старых записей
        if (stats.size > maxEntries) {
            val toRemove = stats.entries.sortedBy { it.value }.take(maxEntries / 10)
            toRemove.forEach { stats.remove(it.key) }
        }
    }

    fun getUsageCount(descriptor: DeclarationDescriptor): Int {
        return stats[descriptor.stableKey()] ?: 0
    }

    @SubscribeEvent
    fun saveToFile(event: LevelEvent.Save) {
        file.outputStream().use {
            NBTFormat.serialize(stats).save(it)
        }
    }
}

private fun DeclarationDescriptor.stableKey(): String {
    return when (this) {
        is ClassDescriptor -> "c:${fqNameSafe.asString()}"
        is FunctionDescriptor -> "f:${containingDeclaration.stableKey()}:${name.asString()}:${valueParameters.size}"
        is PropertyDescriptor -> "p:${containingDeclaration.stableKey()}:${name.asString()}"
        else -> "o:${name.asString()}"
    }
}

data class ScoredDescriptor(
    val matchResult: MatchResult,
    val usageCount: Int,
    val length: Int,
    val typePriority: Int,
    val exactMatch: Boolean,
    val descriptor: DeclarationDescriptor,
    val imports: List<String> = emptyList()
)

fun filterCandidates(
    prefix: String,
    descriptors: List<DeclarationDescriptor>
): List<ScoredDescriptor> {
    val scored = descriptors.mapNotNull { descriptor ->
        val name = descriptor.name.asString()
        val matchScore = fuzzyCamelHumpScore(prefix, name) ?: return@mapNotNull null
        if (matchScore.score < 0) null else ScoredDescriptor(
            matchResult = matchScore,
            usageCount = UsageStatistics.getUsageCount(descriptor),
            length = name.length,
            typePriority = getTypePriority(descriptor),
            exactMatch = name.equals(prefix, ignoreCase = true),
            descriptor = descriptor
        )
    }

    return scored
}

fun sortedCandidates(scored: List<ScoredDescriptor>): List<ScoredDescriptor> {
    return scored.sortedWith(
        compareBy(
            { !it.exactMatch },     // Точные совпадения - первыми
            { -it.matchResult.score },     // Основное качество совпадения
            { -it.usageCount },     // Частота использования
            { it.length },          // Короткие имена выше
            { it.typePriority }     // Тип элемента
        )
    )
}

private fun getTypePriority(descriptor: DeclarationDescriptor): Int {
    return when (descriptor) {
        is ClassDescriptor -> 0
        is FunctionDescriptor -> 1
        is PropertyDescriptor -> 2
        is ValueParameterDescriptor -> 3
        else -> 4
    }
}
