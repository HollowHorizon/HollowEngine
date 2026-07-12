package ru.hollowhorizon.hollowengine.common.config

@ConfigName("hollowengine")
object HollowEngineConfig : Config() {
    val debugMode by property(true)

    @PropertyComment("Enables editor button in top left corner")
    @PropertyName("edit_mode")
    var editMode by property(EditMode.ENABLED)

    @PropertyComment("Font size used by the Hollow IDE code editor")
    @PropertyName("ide_editor_font_size")
    @PropertyRange(6.0f, 36.0f)
    var ideEditorFontSize by property(12f)

    @PropertyComment("Mods that be available inside scripting & compilation")
    @PropertyName("scripting_mods")
    var scriptingMods by list("hollowengine")
}

enum class EditMode {
    DISABLED, ENABLED, CHAT_ONLY
}
