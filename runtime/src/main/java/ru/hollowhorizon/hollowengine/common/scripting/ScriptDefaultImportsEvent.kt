package ru.hollowhorizon.hollowengine.common.scripting

import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

/** Lets active addons register additional script suffixes for the normal compiler and editor. */
class ScriptDefinitionsEvent(
    val providers: MutableList<ScriptClassProvider>,
) : Event {
    companion object : EventHandler<ScriptDefinitionsEvent>()
}

/** Lets active addons supply imports for a script type without putting addon packages in the engine. */
class ScriptDefaultImportsEvent(
    val suffix: String,
    val defaultImports: MutableList<String>,
) : Event {
    companion object : EventHandler<ScriptDefaultImportsEvent>()
}
