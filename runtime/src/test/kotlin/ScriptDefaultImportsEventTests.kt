package ru.hollowhorizon.hollowengine.common.scripting

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import ru.hollowhorizon.hollowengine.common.events.eventListenerOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptDefaultImportsEventTests {
    @Test
    fun `addon imports follow the active event subscription`() {
        val scope = CoroutineScope(Job())
        val contributedImport = "example.addon.*"
        val listener = eventListenerOf<ScriptDefaultImportsEvent> { event ->
            if (event.suffix == NODE_SCRIPT_EXTENSION) event.defaultImports += contributedImport
        }
        try {
            ScriptDefaultImportsEvent.register(scope, listener)
            assertTrue(contributedImport in DefaultScriptDefinitions.providerFor("example.node.kts")!!.defaultImports)
            assertFalse(contributedImport in DefaultScriptDefinitions.providerFor("example.reload.kts")!!.defaultImports)
        } finally {
            scope.cancel()
        }
        assertFalse(contributedImport in DefaultScriptDefinitions.providerFor("example.node.kts")!!.defaultImports)
    }

    @Test
    fun `addon script definitions follow the active event subscription`() {
        val scope = CoroutineScope(Job())
        val extension = "custom.kts"
        val listener = eventListenerOf<ScriptDefinitionsEvent> { event ->
            event.providers += ScriptClassProvider(extension, "kotlin.Any")
        }
        try {
            ScriptDefinitionsEvent.register(scope, listener)
            assertTrue(DefaultScriptDefinitions.providerFor("example.$extension")!!.extension == extension)
        } finally {
            scope.cancel()
        }
        assertFalse(DefaultScriptDefinitions.providerFor("example.$extension")!!.extension == extension)
    }
}
