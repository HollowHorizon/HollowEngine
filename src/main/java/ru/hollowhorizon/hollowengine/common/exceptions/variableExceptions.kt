package ru.hollowhorizon.hollowengine.common.exceptions

class StoryVariableNotFoundException(name: String) : StoryEventException("Variable $name not found!")

class StoryVariableWrongTypeException(name: String) : StoryEventException("Variable $name has wrong type!")