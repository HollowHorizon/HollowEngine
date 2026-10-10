package ru.hollowhorizon.hollowengine.common.scripting.nodes

import org.junit.jupiter.api.io.TempDir
import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptCache
import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptFingerprint.Fingerprint
import ru.hollowhorizon.hollowengine.common.scripting.compiling.CompiledScript
import ru.hollowhorizon.hollowengine.common.scripting.compiling.ScriptResult
import ru.hollowhorizon.hollowengine.common.scripting.compiling.loadKotlinCompiledScriptFromJar
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class NodeReloadPlanTest {
    private val old = Fingerprint("old", "old-layout", "runtime")
    private val changed = Fingerprint("new", "new-layout", "runtime")

    @Test
    fun `unchanged instances are not recompiled or replaced`() {
        var compilations = 0
        val plan = NodeReloadPlan({ old }, { compilations++; Result.success(Program(old)) })

        assertNull(plan.replacement("node", old))
        assertEquals(0, compilations)
    }

    @Test
    fun `all instances share one compilation and fingerprint check per reload`() {
        var checks = 0
        var compilations = 0
        val program = Program(changed)
        val plan = NodeReloadPlan({ checks++; changed }, { compilations++; Result.success(program) })

        repeat(100) { assertSame(program, plan.replacement("node", old)) }
        assertNull(plan.replacement("node", changed))
        assertEquals(1, checks)
        assertEquals(1, compilations)
    }

    @Test
    fun `failed compilation keeps every running instance and is retried on the next reload`() {
        var compilations = 0
        val compile: (String) -> Result<CompiledScript> = {
            compilations++
            Result.failure(IllegalArgumentException("Invalid script"))
        }
        val plan = NodeReloadPlan({ changed }, compile)
        repeat(2) { assertNull(plan.replacement("node", old)) }
        assertEquals(1, compilations)

        assertNull(NodeReloadPlan({ changed }, compile).replacement("node", old))
        assertEquals(2, compilations)
    }

    @Test
    fun `outdated fallback bytecode does not replace a running node`() {
        val plan = NodeReloadPlan({ changed }, { Result.success(Program(old)) })
        assertNull(plan.replacement("node", old))
    }

    @Test
    fun `missing sources keep the existing instance`() {
        var compilations = 0
        val plan = NodeReloadPlan({ null }, { compilations++; Result.success(Program(changed)) })
        assertNull(plan.replacement("node", old))
        assertEquals(0, compilations)
    }

    @Test
    fun `changing attachment receivers keeps the running instance`() {
        val plan = NodeReloadPlan({ changed }, { Result.success(Program(changed)) })
        assertNull(plan.replacement("node", old, expectedReceivers = 2))
    }

    @Test
    fun `jar loading remembers the bytecode fingerprint even when sources change`(@TempDir root: File) {
        val file = root.resolve("node.jar")
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes[Attributes.Name.MAIN_CLASS] = "TestNode"
            ScriptCache.stamp(mainAttributes, old)
        }
        JarOutputStream(file.outputStream(), manifest).use { }
        val loaded = file.loadKotlinCompiledScriptFromJar()
        assertEquals(old, loaded.fingerprint)

        ScriptCache.stamp(manifest.mainAttributes, changed)
        JarOutputStream(file.outputStream(), manifest).use { }
        assertEquals(old, loaded.fingerprint)
        assertEquals(changed, file.loadKotlinCompiledScriptFromJar().fingerprint)
    }

    private class Program(override val fingerprint: Fingerprint) : CompiledScript {
        override val name = "node"
        override val type: KClass<*> = NodeScript::class
        override val implicitReceiverCount = 1
        override val isClientSide = false

        override fun <T> execute(body: ScriptEvaluationConfiguration.Builder.() -> Unit): Result<T> =
            error("Reload planning must not execute scripts")

        override fun evaluate(body: ScriptEvaluationConfiguration.Builder.() -> Unit): Result<ScriptResult> =
            error("Reload planning must not execute scripts")
    }
}
