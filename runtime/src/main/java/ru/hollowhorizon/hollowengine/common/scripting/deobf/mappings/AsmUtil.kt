package ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings

import org.objectweb.asm.ClassReader


fun MappedField.index(owner: String, namespace: Int): String = "$owner.${names[namespace]}"
fun MappedMethod.index(owner: String, namespace: Int): String = "$owner.${names[namespace]}$desc"
fun Mappings.asASMMapping(
    from: String,
    to: String,
    includeMethods: Boolean = true,
    includeFields: Boolean = true,
): Map<String, String> = buildMap {
    val fromIndex = namespaces.indexOf(from)
    val toIndex = namespaces.indexOf(to)

    require(fromIndex >= 0) { "Namespace $from does not exist!" }
    require(toIndex >= 0) { "Namespace $to does not exist!" }

    classes.forEach { clz ->
        val owner = clz.names[fromIndex]
        put(owner, clz.names[toIndex])
        if (includeFields) clz.fields.forEach { put(it.index(owner, fromIndex), it.names[toIndex]) }
        if (includeMethods) clz.methods.forEach { put(it.index(owner, fromIndex), it.names[toIndex]) }
    }
}

internal inline fun walkInheritance(
    loader: (name: String) -> ByteArray?,
    start: String,
    isEnd: (curr: String) -> Boolean,
): String? {
    val queue = ArrayDeque<String>()
    val seen = hashSetOf<String>()
    queue.addLast(start)

    while (queue.isNotEmpty()) {
        val curr = queue.removeLast()
        if (isEnd(curr)) return curr

        val bytes = loader(curr) ?: continue
        val reader = ClassReader(bytes)

        reader.superName?.let { if (seen.add(it)) queue.addLast(it) }
        queue += reader.interfaces.filter { seen.add(it) }
    }

    return null
}