package ru.hollowhorizon.hollowengine.common.scripting.ide

/** A case-insensitive completion match. Lower [score] values are better. */
data class CompletionMatch(
    val score: Int,
    val ranges: List<IntRange>,
)

/**
 * Matches ordinary prefixes as well as camel-hump/subsequence input (`HollEng` -> `HollowEngine`).
 * The score intentionally favours an exact/prefix match, word boundaries, compact matches and,
 * finally, shorter candidates.
 */
fun matchCompletion(pattern: String, candidate: String): CompletionMatch? {
    if (pattern.isEmpty()) return CompletionMatch(candidate.length, emptyList())
    if (candidate.isEmpty() || pattern.length > candidate.length) return null

    val positions = IntArray(pattern.length)
    var searchFrom = 0
    for (patternIndex in pattern.indices) {
        val expected = pattern[patternIndex]
        var found = -1
        for (candidateIndex in searchFrom until candidate.length) {
            if (candidate[candidateIndex].equals(expected, ignoreCase = true)) {
                found = candidateIndex
                break
            }
        }
        if (found < 0) return null
        positions[patternIndex] = found
        searchFrom = found + 1
    }

    var gaps = 0
    var boundaryMisses = 0
    var caseMisses = 0
    for (index in positions.indices) {
        val position = positions[index]
        if (index > 0) gaps += position - positions[index - 1] - 1
        if (!candidate[position].isCompletionBoundary(candidate, position)) boundaryMisses++
        if (candidate[position] != pattern[index]) caseMisses++
    }
    val prefix = positions.first() == 0 && positions.last() == pattern.lastIndex
    val exact = prefix && pattern.length == candidate.length
    val score = when {
        exact -> 0
        prefix -> 100 + candidate.length - pattern.length
        else -> 1_000 + positions.first() * 40 + gaps * 18 + boundaryMisses * 3 +
                caseMisses + candidate.length - pattern.length
    }
    return CompletionMatch(score, positions.toRanges())
}

fun completionMatches(pattern: String?, candidate: String): Boolean =
    pattern == null || matchCompletion(pattern, candidate) != null

private fun Char.isCompletionBoundary(candidate: String, index: Int): Boolean {
    if (index == 0) return true
    val previous = candidate[index - 1]
    return !previous.isLetterOrDigit() || previous == '_' || previous == '-' ||
            isUpperCase() && previous.isLowerCase() || isDigit() && !previous.isDigit()
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
