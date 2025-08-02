package ru.hollowhorizon.hollowengine.common.project.kt

object CamelHumpMatcher {
    fun matches(pattern: String, name: String): Boolean {
        if (pattern.isEmpty()) return true
        if (pattern.length > name.length) return false

        return matches(pattern, 0, name, 0, isHumpBoundary(0, name))
    }

    private fun matches(
        pattern: String,
        patternIndex: Int,
        name: String,
        nameIndex: Int,
        atBoundary: Boolean
    ): Boolean {
        if (patternIndex >= pattern.length) return true
        if (nameIndex >= name.length) return false

        val patternChar = pattern[patternIndex]
        val nameChar = name[nameIndex]
        val nextBoundary = isHumpBoundary(nameIndex + 1, name)

        return when {
            patternChar.isUpperCase() -> {
                if (atBoundary && nameChar == patternChar) {
                    matches(pattern, patternIndex + 1, name, nameIndex + 1, nextBoundary)
                } else {
                    matches(pattern, patternIndex, name, nameIndex + 1, nextBoundary)
                }
            }

            else -> {
                if (atBoundary && nameChar.equals(patternChar, ignoreCase = true)) {
                    if (nameChar == patternChar) {
                        matches(pattern, patternIndex + 1, name, nameIndex + 1, nextBoundary)
                    } else {
                        matches(pattern, patternIndex, name, nameIndex + 1, nextBoundary) ||
                                matches(pattern, patternIndex + 1, name, nameIndex + 1, nextBoundary)
                    }
                } else {
                    if (nameChar.equals(patternChar, ignoreCase = true)) {
                        matches(pattern, patternIndex + 1, name, nameIndex + 1, nextBoundary)
                    } else {
                        matches(pattern, patternIndex, name, nameIndex + 1, nextBoundary)
                    }
                }
            }
        }
    }

    private fun isHumpBoundary(index: Int, name: String): Boolean {
        if (index == 0) return true
        if (index >= name.length) return false

        val prev = name[index - 1]
        val current = name[index]

        if (!prev.isLetterOrDigit()) return true

        if (prev.isLowerCase() && current.isUpperCase()) return true

        if (prev.isDigit() && current.isLetter()) return true

        return current.isUpperCase() && index + 1 < name.length && name[index + 1].isLowerCase()
    }
}