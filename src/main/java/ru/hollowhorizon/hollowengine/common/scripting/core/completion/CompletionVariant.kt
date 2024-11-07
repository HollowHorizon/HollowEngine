package ru.hollowhorizon.hollowengine.common.scripting.core.completion

data class CompletionVariant(
    val text: String,
    val displayText: String,
    val tail: String,
    val icon: String,
) {
    override fun toString(): String {
        return displayText
    }
}