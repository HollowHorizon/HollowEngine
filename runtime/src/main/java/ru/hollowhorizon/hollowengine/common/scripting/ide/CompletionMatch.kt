package ru.hollowhorizon.hollowengine.common.scripting.ide

/** A completion match. Lower [score] values are better. */
data class CompletionMatch(
    val score: Int,
    val ranges: List<IntRange>,
)

/**
 * How much the case of what was typed has to agree with the candidate, mirroring IDEA's
 * *Match case* option. Case always affects ranking; this decides when it also rejects a candidate.
 */
enum class CompletionCaseSensitivity {
    /** Case only ranks: `min` still offers `Minecraft`, below `minValue`. */
    NONE,

    /** The first typed character must agree - `min` no longer offers `Minecraft`, `Min` does. */
    FIRST_LETTER,

    /** Every typed character must agree, humps included: `MRL`, never `Mrl`. */
    ALL;

    companion object {
        /** What the editor and the compiler's name filter both use; IDEA's default too. */
        val DEFAULT = FIRST_LETTER
    }
}

/**
 * Typed characters run through a candidate contiguously, and jump forward is only allowed
 * onto the start of a new word (`HollEng` -> `Holl`+`Eng`ine). Typing `iner` therefore
 * does not drag in `Minecraft`, and under the default [CompletionCaseSensitivity]
 * neither does `min`. `MRL` finds `MinecraftResourceLoader`, `mrl` does not.
 */
fun matchCompletion(
    pattern: String,
    candidate: String,
    caseSensitivity: CompletionCaseSensitivity = CompletionCaseSensitivity.DEFAULT,
): CompletionMatch? {
    if (pattern.isEmpty()) return CompletionMatch(0, emptyList())
    if (candidate.isEmpty() || pattern.length > candidate.length) return null
    prefixMatch(pattern, candidate, caseSensitivity)?.let { return it }
    if (!isSubsequence(pattern, candidate)) return null
    return CompletionMatcher(pattern, candidate, caseSensitivity).best()
}

fun completionMatches(
    pattern: String?,
    candidate: String,
    caseSensitivity: CompletionCaseSensitivity = CompletionCaseSensitivity.DEFAULT,
): Boolean {
    if (pattern.isNullOrEmpty()) return true
    if (candidate.isEmpty() || pattern.length > candidate.length) return false
    if (prefixMatch(pattern, candidate, caseSensitivity) != null) return true
    if (!isSubsequence(pattern, candidate)) return false
    return CompletionMatcher(pattern, candidate, caseSensitivity).matches()
}

/**
 * The overwhelmingly common case, what was typed is simply how the candidate starts. No placement
 * can beat it, so it never reaches the search below. A prefix the case rules reject still can:
 * `Min` is not how `minMin` starts, but its second word does start that way.
 */
private fun prefixMatch(
    pattern: String,
    candidate: String,
    caseSensitivity: CompletionCaseSensitivity,
): CompletionMatch? {
    if (!candidate.startsWith(pattern, ignoreCase = true)) return null
    var caseMisses = 0
    for (index in pattern.indices) {
        if (pattern[index] == candidate[index]) continue
        if (!caseSensitivity.accepts(index, pattern[index], candidate[index])) return null
        caseMisses++
    }
    val tier = when {
        pattern.length == candidate.length -> if (caseMisses == 0) TierExactCase else TierExact
        caseMisses == 0 -> TierPrefixCase
        else -> TierPrefix
    }
    val score = tier * TierStep + caseMisses * CaseCost + (candidate.length - pattern.length) * LengthCost
    return CompletionMatch(score, listOf(0..pattern.lastIndex))
}

private fun CompletionCaseSensitivity.accepts(patternIndex: Int, patternChar: Char, candidateChar: Char): Boolean {
    if (patternChar == candidateChar) return true
    return when (this) {
        CompletionCaseSensitivity.NONE -> true
        CompletionCaseSensitivity.FIRST_LETTER -> patternIndex > 0 || !patternChar.hasCase() || !candidateChar.hasCase()
        CompletionCaseSensitivity.ALL -> !patternChar.hasCase() || !candidateChar.hasCase()
    }
}

private fun Char.hasCase(): Boolean = isUpperCase() || isLowerCase()

private const val TierStep = 1_000_000
private const val TierExactCase = 0
private const val TierExact = 1
private const val TierPrefixCase = 2
private const val TierPrefix = 3
private const val TierFirstWord = 4
private const val TierLaterWord = 5

private const val JumpCost = 800
private const val SkippedWordCost = 120
private const val CaseCost = 12
private const val LengthCost = 1

private const val Unset = Int.MIN_VALUE
private const val Impossible = Int.MAX_VALUE

private fun isSubsequence(pattern: String, candidate: String): Boolean {
    var patternIndex = 0
    for (candidateIndex in candidate.indices) {
        if (candidate[candidateIndex].equals(pattern[patternIndex], ignoreCase = true)) {
            patternIndex++
            if (patternIndex == pattern.length) return true
        }
    }
    return false
}

private class CompletionMatcher(
    private val pattern: String,
    private val candidate: String,
    private val caseSensitivity: CompletionCaseSensitivity,
) {
    private val wordStart = BooleanArray(candidate.length)
    private val wordIndex = IntArray(candidate.length)
    private val wordStarts: IntArray
    private val cost = IntArray(pattern.length * candidate.length) { Unset }
    private val next = IntArray(pattern.length * candidate.length)

    init {
        val starts = ArrayList<Int>()
        var word = -1
        for (index in candidate.indices) {
            val isStart = candidate.isWordStart(index)
            if (isStart) {
                word++
                starts += index
            }
            wordStart[index] = isStart
            wordIndex[index] = word.coerceAtLeast(0)
        }
        wordStarts = starts.toIntArray()
    }

    fun matches(): Boolean = wordStarts.any { start ->
        matchesAt(0, start) && costAt(0, start) != Impossible
    }

    fun best(): CompletionMatch? {
        var bestScore = Int.MAX_VALUE
        var bestPositions: IntArray? = null
        for (start in wordStarts) {
            if (!matchesAt(0, start)) continue
            val placement = costAt(0, start)
            if (placement == Impossible) continue
            val positions = positionsFrom(start)
            val score = scoreOf(positions, placement)
            if (score < bestScore) {
                bestScore = score
                bestPositions = positions
            }
        }
        val positions = bestPositions ?: return null
        return CompletionMatch(bestScore, positions.toRanges())
    }

    private fun scoreOf(positions: IntArray, placement: Int): Int {
        val contiguous = positions.last() - positions.first() == pattern.lastIndex
        val tier = when {
            positions.first() != 0 -> TierLaterWord
            !contiguous -> TierFirstWord
            pattern.length == candidate.length -> if (pattern == candidate) TierExactCase else TierExact
            candidate.startsWith(pattern) -> TierPrefixCase
            else -> TierPrefix
        }
        return tier * TierStep + placement + (candidate.length - pattern.length) * LengthCost
    }

    private fun costAt(patternIndex: Int, candidateIndex: Int): Int {
        val key = patternIndex * candidate.length + candidateIndex
        val cached = cost[key]
        if (cached != Unset) return cached

        val caseCost = if (pattern[patternIndex] == candidate[candidateIndex]) 0 else CaseCost
        if (patternIndex == pattern.lastIndex) {
            cost[key] = caseCost
            next[key] = -1
            return caseCost
        }

        var best = Impossible
        var bestNext = -1
        val continuation = candidateIndex + 1
        if (matchesAt(patternIndex + 1, continuation)) {
            val tail = costAt(patternIndex + 1, continuation)
            if (tail != Impossible) {
                best = tail
                bestNext = continuation
            }
        }
        for (start in wordStarts) {
            if (start <= continuation) continue
            if (!matchesAt(patternIndex + 1, start)) continue
            val tail = costAt(patternIndex + 1, start)
            if (tail == Impossible) continue
            val skipped = (wordIndex[start] - wordIndex[candidateIndex] - 1).coerceAtLeast(0)
            val total = tail + JumpCost + skipped * SkippedWordCost
            if (total < best) {
                best = total
                bestNext = start
            }
        }

        val result = if (best == Impossible) Impossible else best + caseCost
        cost[key] = result
        next[key] = bestNext
        return result
    }

    private fun positionsFrom(start: Int): IntArray {
        val positions = IntArray(pattern.length)
        var candidateIndex = start
        for (patternIndex in pattern.indices) {
            positions[patternIndex] = candidateIndex
            candidateIndex = next[patternIndex * candidate.length + candidateIndex]
        }
        return positions
    }

    private fun matchesAt(patternIndex: Int, candidateIndex: Int): Boolean {
        if (candidateIndex >= candidate.length) return false
        val patternChar = pattern[patternIndex]
        val candidateChar = candidate[candidateIndex]
        if (!patternChar.equals(candidateChar, ignoreCase = true)) return false
        return caseSensitivity.accepts(patternIndex, patternChar, candidateChar)
    }
}

private fun String.isWordStart(index: Int): Boolean {
    if (index == 0) return true
    val current = this[index]
    val previous = this[index - 1]
    return when {
        !previous.isLetterOrDigit() -> current.isLetterOrDigit()
        current.isUpperCase() && !previous.isUpperCase() -> true
        current.isUpperCase() && index + 1 < length && this[index + 1].isLowerCase() -> true
        current.isDigit() != previous.isDigit() -> true
        else -> false
    }
}

private fun IntArray.toRanges(): List<IntRange> {
    if (isEmpty()) return emptyList()
    val ranges = ArrayList<IntRange>()
    var start = first()
    var end = start
    for (index in 1 until size) {
        val position = this[index]
        if (position == end + 1) {
            end = position
        } else {
            ranges += start..end
            start = position
            end = position
        }
    }
    ranges += start..end
    return ranges
}
