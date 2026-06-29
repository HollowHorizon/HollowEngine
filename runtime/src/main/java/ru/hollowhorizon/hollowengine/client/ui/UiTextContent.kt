package ru.hollowhorizon.hollowengine.client.ui

data class UiTextContent(
    val segments: List<UiTextSegment>,
) {
    fun resolve(): UiResolvedTextContent {
        return UiResolvedTextContent(
            segments.map { it.resolve() },
        )
    }

    fun asTemplate(): String = buildString {
        segments.forEach { segment ->
            if (segment is UiTextSegment.Text) append(segment.value.template)
        }
    }

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

    companion object {
        fun plain(value: UiBoundString): UiTextContent = UiTextContent(listOf(UiTextSegment.Text(value)))

        fun plain(value: String): UiTextContent = plain(value.bound())
    }
}

sealed interface UiTextSegment {
    fun resolve(): UiResolvedTextSegment

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
    ) : UiTextSegment {
        override fun resolve(): UiResolvedTextSegment {
            return UiResolvedTextSegment.Text(value.resolve(), style)
        }
    }

    data class Image(
        val source: UiBoundString,
        val width: Float,
        val height: Float,
        val align: UiInlineAlign = UiInlineAlign.BASELINE,
        val alt: String = "",
    ) : UiTextSegment {
        override fun resolve(): UiResolvedTextSegment {
            return UiResolvedTextSegment.Image(source.resolve(), width, height, align, alt)
        }
    }

    data class Widget(
        val id: String,
        val align: UiInlineAlign = UiInlineAlign.BASELINE,
        val alt: String = "",
    ) : UiTextSegment {
        override fun resolve(): UiResolvedTextSegment {
            return UiResolvedTextSegment.Widget(id, align, alt)
        }
    }

    data class Pause(
        val delayMillis: Long,
    ) : UiTextSegment {
        override fun resolve(): UiResolvedTextSegment {
            return UiResolvedTextSegment.Pause(delayMillis)
        }
    }
}

data class UiResolvedTextContent(
    val segments: List<UiResolvedTextSegment>,
) {
    val text: String = buildString {
        segments.forEach { segment ->
            if (segment is UiResolvedTextSegment.Text) append(segment.value)
        }
    }

    fun toRichText(widgetMetrics: Map<String, UiInlineWidgetMetrics> = emptyMap()): UiRichText {
        return UiRichText(
            segments.mapNotNull { segment ->
                when (segment) {
                    is UiResolvedTextSegment.Text -> UiInlineItem.Text(segment.value, segment.style)
                    is UiResolvedTextSegment.Image -> UiInlineItem.Image(
                        source = segment.source,
                        width = segment.width,
                        height = segment.height,
                        align = segment.align,
                        alt = segment.alt,
                    )

                    is UiResolvedTextSegment.Widget -> UiInlineItem.Widget(
                        id = segment.id,
                        width = widgetMetrics[segment.id]?.width ?: 0f,
                        height = widgetMetrics[segment.id]?.height ?: 0f,
                        align = segment.align,
                        alt = segment.alt,
                    )

                    is UiResolvedTextSegment.Pause -> null
                }
            },
        )
    }

    fun visibleBy(typing: UiTyping?, elapsedMillis: Long): UiResolvedTextContent {
        if (typing == null) return this
        val delayedElapsed = elapsedMillis - typing.delayMillis.coerceAtLeast(0L)
        if (delayedElapsed < 0L) return UiResolvedTextContent(emptyList())
        val characterCount = segments.sumOf { (it as? UiResolvedTextSegment.Text)?.value?.length ?: 0 }
        if (characterCount == 0) return this
        val activeDuration = typing.durationMillis ?: (characterCount * UiTyping.AutoMillisPerCharacter)
        if (activeDuration <= 0L) return withoutPauses()

        val result = mutableListOf<UiResolvedTextSegment>()
        var visibleCharacters = 0
        var pausesBefore = 0L
        fun visibleAt(characterIndex: Int): Boolean {
            val progress = characterIndex.toFloat() / characterCount.toFloat()
            val activeTime = (typing.easing.inverse(progress) * activeDuration).toLong()
            return delayedElapsed >= activeTime + pausesBefore
        }

        for (segment in segments) {
            when (segment) {
                is UiResolvedTextSegment.Image -> result += segment
                is UiResolvedTextSegment.Widget -> result += segment
                is UiResolvedTextSegment.Pause -> pausesBefore += segment.delayMillis
                is UiResolvedTextSegment.Text -> {
                    val length = segment.value.length
                    var visibleInSegment = 0
                    while (visibleInSegment < length && visibleAt(visibleCharacters + visibleInSegment + 1)) {
                        visibleInSegment++
                    }
                    if (visibleInSegment > 0) {
                        result += segment.copy(value = segment.value.take(visibleInSegment))
                    }
                    visibleCharacters += length
                    if (visibleInSegment < length) break
                }
            }
        }
        return UiResolvedTextContent(result)
    }

    private fun withoutPauses(): UiResolvedTextContent {
        return UiResolvedTextContent(segments.filterNot { it is UiResolvedTextSegment.Pause })
    }
}

sealed interface UiResolvedTextSegment {
    data class Text(
        val value: String,
        val style: UiInlineStyle = UiInlineStyle(),
    ) : UiResolvedTextSegment

    data class Image(
        val source: String,
        val width: Float,
        val height: Float,
        val align: UiInlineAlign = UiInlineAlign.BASELINE,
        val alt: String = "",
    ) : UiResolvedTextSegment

    data class Widget(
        val id: String,
        val align: UiInlineAlign = UiInlineAlign.BASELINE,
        val alt: String = "",
    ) : UiResolvedTextSegment

    data class Pause(
        val delayMillis: Long,
    ) : UiResolvedTextSegment
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
