package ru.hollowhorizon.hollowstory.common.exceptions

class StoryVariableNotFoundException(name: String) : StoryEventException("Variable $name not found!")

class StoryVariableWrongTypeException(name: String) : StoryEventException("Variable $name has wrong type!")