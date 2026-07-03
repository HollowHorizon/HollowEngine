package ru.hollowhorizon.hollowengine.client.ui.widgets

import ru.hollowhorizon.hollowengine.client.ui.bound
import ru.hollowhorizon.hollowengine.client.ui.style.*

data class UiTextContent(
    val segments: List<UiTextSegment>,
) {
    val text: String = buildString {
        segments.forEach { segment ->
            if (segment is UiTextSegment.Text) append(segment.value.template)
        }
    }

    fun asTemplate(): String = text

    fun toRichText(widgetMetrics: Map<String, UiInlineWidgetMetrics> = emptyMap()): UiRichText {
        return UiRichText(
            segments.mapNotNull { segment ->
                when (segment) {
                    is UiTextSegment.Text -> UiInlineItem.Text(segment.value.template, segment.style)
                    is UiTextSegment.Image -> UiInlineItem.Image(
                        source = segment.source.template,
                        width = segment.width,
                        height = segment.height,
                        align = segment.align,
                        alt = segment.alt,
                    )

                    is UiTextSegment.Widget -> UiInlineItem.Widget(
                        id = segment.id,
                        width = widgetMetrics[segment.id]?.width ?: 0f,
                        height = widgetMetrics[segment.id]?.height ?: 0f,
                        align = segment.align,
                        alt = segment.alt,
                    )

                    is UiTextSegment.Pause -> null
                }
            },
        )
    }

    fun visibleBy(typing: UiTyping?, elapsedMillis: Long): UiTextContent {
        if (typing == null) return this
        val delayedElapsed = elapsedMillis - typing.delayMillis.coerceAtLeast(0L)
        if (delayedElapsed < 0L) return UiTextContent(emptyList())
        val characterCount = text.length
        if (characterCount == 0) return this
        val activeDuration = typing.durationMillis ?: (characterCount * UiTyping.AutoMillisPerCharacter)
        if (activeDuration <= 0L) return withoutPauses()

        val result = mutableListOf<UiTextSegment>()
        var visibleCharacters = 0
        var pausesBefore = 0L
        fun visibleAt(characterIndex: Int): Boolean {
            val progress = characterIndex.toFloat() / characterCount.toFloat()
            val activeTime = (typing.easing.inverse(progress) * activeDuration).toLong()
            return delayedElapsed >= activeTime + pausesBefore
        }

        for (segment in segments) {
            when (segment) {
                is UiTextSegment.Image -> result += segment
                is UiTextSegment.Widget -> result += segment
                is UiTextSegment.Pause -> pausesBefore += segment.delayMillis
                is UiTextSegment.Text -> {
                    val value = segment.value.template
                    val length = value.length
                    var visibleInSegment = 0
                    while (visibleInSegment < length && visibleAt(visibleCharacters + visibleInSegment + 1)) {
                        visibleInSegment++
                    }
                    if (visibleInSegment > 0) {
                        result += segment.copy(value = value.take(visibleInSegment).bound())
                    }
                    visibleCharacters += length
                    if (visibleInSegment < length) break
                }
            }
        }
        return UiTextContent(result)
    }

    private fun withoutPauses(): UiTextContent {
        return UiTextContent(segments.filterNot { it is UiTextSegment.Pause })
    }

    companion object {
        fun plain(value: UiBoundString): UiTextContent = UiTextContent(listOf(UiTextSegment.Text(value)))

        fun plain(value: String): UiTextContent = plain(value.bound())
    }
}

sealed interface UiTextSegment {
    companion object {
        fun inlineWidget(
            id: String,
            align: UiInlineAlign = UiInlineAlign.BASELINE,
            alt: String = "",
        ): Widget = Widget(id, align, alt)
    }

    data class Text(
        val value: UiBoundString,
        val style: UiInlineStyle = UiInlineStyle(),
    ) : UiTextSegment

    data class Image(
        val source: UiBoundString,
        val width: Float,
        val height: Float,
        val align: UiInlineAlign = UiInlineAlign.BASELINE,
        val alt: String = "",
    ) : UiTextSegment

    data class Widget(
        val id: String,
        val align: UiInlineAlign = UiInlineAlign.BASELINE,
        val alt: String = "",
    ) : UiTextSegment

    data class Pause(
        val delayMillis: Long,
    ) : UiTextSegment
}

data class UiTyping(
    val durationMillis: Long? = null,
    val easing: TransitionEasing = TransitionEasing.LINEAR,
    val delayMillis: Long = 0L,
) {
    companion object {
        const val AutoMillisPerCharacter = 35L
    }
}
