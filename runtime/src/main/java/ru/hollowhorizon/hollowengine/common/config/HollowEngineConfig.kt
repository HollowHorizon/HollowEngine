package ru.hollowhorizon.hollowengine.common.config

@ConfigName("hollowengine")
object HollowEngineConfig : Config() {
    val debugMode by property(false)

    @PropertyComment("Enables editor button in top left corner")
    @PropertyName("edit_mode")
    var editMode by property(EditMode.ENABLED)
}

enum class EditMode {
    DISABLED, ENABLED, CHAT_ONLY
}