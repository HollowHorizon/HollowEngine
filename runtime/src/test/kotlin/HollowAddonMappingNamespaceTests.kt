package ru.hollowhorizon.hollowengine.common.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HollowAddonMappingNamespaceTests {
    @Test
    fun `fabric development namespace remains named`() {
        assertEquals(
            HollowAddonMappingNamespace.NAMED,
            HollowAddonRuntimeEnvironment.resolveMappingNamespace("named"),
        )
    }

    @Test
    fun `production namespaces remain distinct`() {
        assertEquals(
            HollowAddonMappingNamespace.INTERMEDIARY,
            HollowAddonRuntimeEnvironment.resolveMappingNamespace("intermediary"),
        )
        assertEquals(
            HollowAddonMappingNamespace.OFFICIAL,
            HollowAddonRuntimeEnvironment.resolveMappingNamespace("official"),
        )
    }

    @Test
    fun `unknown namespace is rejected instead of being treated as official`() {
        assertFailsWith<IllegalStateException> {
            HollowAddonRuntimeEnvironment.resolveMappingNamespace("unknown")
        }
    }
}
