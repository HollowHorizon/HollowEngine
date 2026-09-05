package ru.hollowhorizon.hollowengine.runtime.remap

import org.objectweb.asm.commons.Remapper
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.remap
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * A remapper that reports whether it changed anything while a class was being visited.
 */
abstract class TrackingRemapper : Remapper() {
    var changed: Boolean = false
        private set

    protected fun <T> track(original: T, mapped: T): T {
        if (original != mapped) changed = true
        return mapped
    }

    fun resetChanged() {
        changed = false
    }
}

class TableRemapper(private val table: PayloadRemapTable) : TrackingRemapper() {
    override fun map(internalName: String): String =
        track(internalName, table.classes[internalName] ?: internalName)

    override fun mapMethodName(owner: String, name: String, descriptor: String): String {
        if (name == "<init>" || name == "<clinit>") return name
        if (!descriptor.startsWith("(")) return mapFieldName(owner, name, descriptor)
        return track(name, table.methods["$owner.$name$descriptor"] ?: name)
    }

    override fun mapFieldName(owner: String, name: String, descriptor: String?): String =
        track(name, table.fields["$owner.$name"] ?: name)

    override fun mapRecordComponentName(owner: String, name: String, descriptor: String): String =
        mapFieldName(owner, name, descriptor)

    override fun mapSignature(signature: String?, typeSignature: Boolean): String? =
        if (signature?.isEmpty() == true) null else super.mapSignature(signature, typeSignature)
}

private const val PRECOMPILED_SCRIPTS = "META-INF/hollowengine/scripts/"

fun remapPayload(input: File, output: File, remapper: TrackingRemapper) {
    output.parentFile?.mkdirs()
    JarFile(input).use { jar ->
        JarOutputStream(output.outputStream().buffered()).use { out ->
            val written = HashSet<String>()
            jar.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                if (entry.name.endsWith(".RSA") || entry.name.endsWith(".SF")) return@forEach
                if (!written.add(entry.name)) return@forEach

                val bytes = jar.getInputStream(entry).use { it.readBytes() }
                val content = if (entry.name.endsWith(".class") && !entry.name.startsWith(PRECOMPILED_SCRIPTS)) {
                    remapper.resetChanged()
                    val remapped = bytes.remap(remapper)
                    if (remapper.changed) remapped else bytes
                } else {
                    bytes
                }

                out.putNextEntry(JarEntry(entry.name))
                out.write(content)
                out.closeEntry()
            }
        }
    }
}

fun PayloadRemapTable.applyTo(input: File, output: File) = remapPayload(input, output, TableRemapper(this))
