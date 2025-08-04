package ru.hollowhorizon.hollowengine.common.project.kt

object CamelHumpMatcher {
    fun matches(pattern: String, name: String): Boolean {
        val pattern = pattern.splitCamelCase()
        val candidate = name.splitCamelCase()

        for (i in pattern.indices) {
            val patternWord = pattern[i]
            val candidateWord = candidate.getOrNull(i)
            if (candidateWord == null) return false

            if(!candidateWord.startsWith(patternWord)) return false
        }

        return true
    }

    fun String.splitCamelCase(): List<String> {
        val words = mutableListOf<String>()
        var word = ""
        for (c in this) {
            if (c.isUpperCase()) {
                if (word.isNotEmpty()) words += word
                word = c.toString()
            } else {
                word += c
            }
        }
        if (word.isNotEmpty()) words += word
        return words
    }
}