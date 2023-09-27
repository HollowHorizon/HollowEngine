package ru.hollowhorizon.hollowengine.common.exceptions

import java.io.Serial

class StoryVariableNotFoundException(name: String) : StoryEventException("Variable $name not found!") {
    companion object {
        @Serial
        private const val serialVersionUID = -45L
    }
}

class StoryVariableWrongTypeException(name: String) : StoryEventException("Variable $name has wrong type!") {
    companion object {
        @Serial
        private const val serialVersionUID = -20222L
    }
}