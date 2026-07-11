package ru.hollowhorizon.hollowengine.client.ui.xml

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.AttributeModifier
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.ScriptEventModifier
import ru.hollowhorizon.hollowengine.client.ui.StateModifier
import ru.hollowhorizon.hollowengine.client.ui.StylePropModifier
import ru.hollowhorizon.hollowengine.client.ui.flattenModifiers
import ru.hollowhorizon.hollowengine.client.ui.state
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiXmlAttributeRegistryTest {
    @Test
    fun `default registry separates handled modifiers from arbitrary custom attributes`() {
        val resolved = UiXmlAttributeRegistry.Default.resolve(
            mapOf(
                "id" to "root",
                "background" to "#ffffff",
                "onClick" to "emit()",
                "dataRole" to "navigation",
            ),
            UiXmlOptions(),
        )

        val modifiers = resolved.modifiers.flattenModifiers()
        assertTrue(modifiers.any { it is StylePropModifier<*> })
        assertTrue(modifiers.any { it is ScriptEventModifier && it.source == "emit()" })
        assertTrue(modifiers.any { it is AttributeModifier && it.name == "data-role" && it.value == "navigation" })
        assertEquals(mapOf("dataRole" to "navigation"), resolved.customAttributes)
    }

    @Test
    fun `custom handlers can consume attributes before default handling`() {
        val registry = UiXmlAttributeRegistry.Default.withHandler(
            UiXmlAttributeHandler { context ->
                if (context.name == "custom-state") {
                    UiXmlAttributeContribution(Modifier.state(context.value))
                } else {
                    null
                }
            }
        )

        val resolved = registry.resolve(mapOf("customState" to "expanded"), UiXmlOptions(attributes = registry))
        val states = resolved.modifiers.flattenModifiers().filterIsInstance<StateModifier>()

        assertEquals(1, states.size)
        assertEquals("expanded", states.single().states.single().name)
        assertEquals(emptyMap(), resolved.customAttributes)
    }
}
