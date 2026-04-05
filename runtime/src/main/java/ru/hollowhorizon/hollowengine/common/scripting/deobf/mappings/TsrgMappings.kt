package ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings

data class TsrgMappings(
    override val namespaces: List<String>,
    override val classes: List<MappedClass>,
    val isV2: Boolean,
) : Mappings

fun TsrgMappings.write(): List<String> =
    if (isV2) TSRGV2MappingsFormat.write(this) else TSRGV1MappingsFormat.write(this)

data object TSRGV1MappingsFormat : MappingsFormat<TsrgMappings> by TSRGMappingsFormat(false)
data object TSRGV2MappingsFormat : MappingsFormat<TsrgMappings> by TSRGMappingsFormat(true)

internal class TSRGMappingsFormat(private val isV2: Boolean) : MappingsFormat<TsrgMappings> {

    override fun detect(lines: List<String>): Boolean {
        if (lines.isEmpty()) return false
        if (isV2) return lines.first().startsWith("tsrg2")
        if (lines.size < 2) return false

        val fc = lines[0]
        val fe = lines[1]

        return fc.indentCount() == 0 && fc.split(' ').size == 2 &&
                fe.indentCount() == 1 && fe.trimStart('\t').split(' ').size == 2
    }

    override fun parse(lines: List<String>): TsrgMappings {
        require(detect(lines)) { "Invalid mappings" }

        val namespaces = if (isV2) {
            lines.first().split(' ').drop(1)
        } else {
            listOf("obf", "srg")
        }

        val mapLines = (if (isV2) lines.drop(1) else lines)
            .dropWhile { it.indentCount() > 0 }
            .filter { it.isNotBlank() }

        val classes = mutableListOf<MappedClass>()
        var currentClass: ClassBuilder? = null
        var currentMethod: MethodBuilder? = null

        for (line in mapLines) {
            val indent = line.indentCount()
            val parts = line.substring(indent).split(' ')

            when (indent) {
                0 -> {
                    currentClass?.let { classes.add(it.build()) }
                    currentClass = ClassBuilder(parts)
                    currentMethod = null
                }

                1 -> {
                    val c = requireNotNull(currentClass) { "Invalid indent: field/method without class" }
                    if ('(' in parts[1]) {
                        currentMethod = MethodBuilder(parts[1], listOf(parts[0]) + parts.drop(2))
                        c.methods.add(currentMethod)
                    } else {
                        c.fields.add(MappedField(parts, emptyList(), null))
                        currentMethod = null
                    }
                }

                2 -> {
                    val m = requireNotNull(currentMethod) { "Invalid indent: parameter without method" }
                    if (parts.size == 1) {
                        require(parts.single() == "static") { "Unrecognized method meta: $parts" }
                    } else {
                        m.parameters.add(MappedParameter(parts.drop(1), parts[0].toInt()))
                    }
                }

                else -> error("Invalid indent level: $indent")
            }
        }
        currentClass?.let { classes.add(it.build()) }

        return TsrgMappings(namespaces, classes, isV2)
    }

    override fun write(mappings: TsrgMappings): List<String> = buildList {
        if (isV2) {
            add("tsrg2 ${mappings.namespaces.joinToString(" ")}")
        } else {
            require(mappings.namespaces.size == 2) {
                "TSRG v1 supports exactly 2 mapping namespaces, found: ${mappings.namespaces}"
            }
        }

        for (c in mappings.classes) {
            add(c.names.joinToString(" "))

            for (f in c.fields) {
                add("\t${f.names.joinToString(" ")}")
            }

            for (m in c.methods) {
                add(buildString {
                    append('\t').append(m.names.first()).append(' ').append(m.desc)
                    for (i in 1 until m.names.size) {
                        append(' ').append(m.names[i])
                    }
                })

                for (p in m.parameters) {
                    add("\t\t${p.index} ${p.names.joinToString(" ")}")
                }
            }
        }
    }

    private fun String.indentCount(): Int {
        var count = 0
        while (count < length && this[count] == '\t') count++
        return count
    }

    private class ClassBuilder(val names: List<String>) {
        val fields = mutableListOf<MappedField>()
        val methods = mutableListOf<MethodBuilder>()

        fun build() = MappedClass(names, emptyList(), fields, methods.map { it.build() })
    }

    private class MethodBuilder(val desc: String, val names: List<String>) {
        val parameters = mutableListOf<MappedParameter>()

        fun build() = MappedMethod(names, emptyList(), desc, parameters, emptyList())
    }
}