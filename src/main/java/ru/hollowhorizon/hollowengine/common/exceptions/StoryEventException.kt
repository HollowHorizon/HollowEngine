package ru.hollowhorizon.hollowengine.common.exceptions

import java.io.Serial

open class StoryEventException(message: String) : RuntimeException(message) {
    companion object {
        @Serial
        private const val serialVersionUID = -37L
    }
}