package ru.hollowhorizon.hollowengine.common.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HollowAddonExtensionsTests {
    @Test
    fun `cleanup removes owned extensions in reverse order`() {
        val point = HollowAddonExtensionPoint("test:point", TestExtension::class)
        val scope = OwnedHollowAddonExtensions("demo", javaClass.classLoader)
        val cleanupOrder = mutableListOf<String>()

        scope.register(point, "first", TestExtension("first"))
        scope.onUnload { cleanupOrder += "first-cleanup" }
        scope.register(point, "second", TestExtension("second"))
        scope.onUnload { cleanupOrder += "second-cleanup" }

        assertEquals(listOf("first", "second"), point.values().map(TestExtension::name))
        scope.cleanup()

        assertTrue(point.values().isEmpty())
        assertEquals(listOf("second-cleanup", "first-cleanup"), cleanupOrder)
    }

    @Test
    fun `qualified identities isolate owners while rejecting duplicate owner keys`() {
        val point = HollowAddonExtensionPoint("test:point", TestExtension::class)
        val first = OwnedHollowAddonExtensions("first-addon", javaClass.classLoader)
        val second = OwnedHollowAddonExtensions("second-addon", javaClass.classLoader)

        first.register(point, "editor", TestExtension("first"))
        second.register(point, "editor", TestExtension("second"))

        assertEquals(
            listOf("first-addon:editor", "second-addon:editor"),
            point.extensions().map { it.qualifiedId },
        )
        assertFailsWith<IllegalArgumentException> {
            first.register(point, "editor", TestExtension("duplicate"))
        }

        first.cleanup()
        second.cleanup()
    }

    @Test
    fun `priority wins before deterministic registration order`() {
        val point = HollowAddonExtensionPoint("test:point", TestExtension::class)
        val scope = OwnedHollowAddonExtensions("demo", javaClass.classLoader)

        scope.register(point, "normal", TestExtension("normal"), priority = 0)
        scope.register(point, "first-high", TestExtension("first-high"), priority = 10)
        scope.register(point, "second-high", TestExtension("second-high"), priority = 10)

        assertEquals(listOf("first-high", "second-high", "normal"), point.values().map(TestExtension::name))
        scope.cleanup()
    }

    @Test
    fun `manual registration close is idempotent`() {
        val point = HollowAddonExtensionPoint("test:point", TestExtension::class)
        val scope = OwnedHollowAddonExtensions("demo", javaClass.classLoader)
        val registration = scope.register(point, "editor", TestExtension("editor"))

        registration.close()
        registration.close()

        assertFalse(registration.isActive)
        assertTrue(point.values().isEmpty())
        scope.cleanup()
    }

    @Test
    fun `closed scope rejects and rolls back late registrations`() {
        val point = HollowAddonExtensionPoint("test:point", TestExtension::class)
        val scope = OwnedHollowAddonExtensions("demo", javaClass.classLoader)
        scope.cleanup()

        assertFailsWith<IllegalStateException> {
            scope.register(point, "late", TestExtension("late"))
        }
        assertTrue(point.values().isEmpty())
    }

    @Test
    fun `extension callbacks use their defining context classloader`() {
        val point = HollowAddonExtensionPoint("test:point", TestExtension::class)
        val loader = object : ClassLoader(javaClass.classLoader) {}
        val scope = OwnedHollowAddonExtensions("demo", loader)
        scope.register(point, "editor", TestExtension("editor"))

        val observed = point.extensions().single().invoke { Thread.currentThread().contextClassLoader }

        assertSame(loader, observed)
        scope.cleanup()
    }

    private data class TestExtension(val name: String)
}
