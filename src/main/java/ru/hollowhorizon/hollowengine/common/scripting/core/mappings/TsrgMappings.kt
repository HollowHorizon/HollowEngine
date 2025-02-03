package ru.hollowhorizon.hollowengine.common.scripting.core.mappings

data class TsrgMappings(
    override val namespaces: List<String>,
    override val classes: List<MappedClass>,
    val isV2: Boolean
) : Mappings

fun TsrgMappings.write(): List<String> = (if (isV2) TSRGV2MappingsFormat else TSRGV1MappingsFormat).write(this)

data object TSRGV1MappingsFormat : MappingsFormat<TsrgMappings> by TSRGMappingsFormat(false)
data object TSRGV2MappingsFormat : MappingsFormat<TsrgMappings> by TSRGMappingsFormat(true)

internal class TSRGMappingsFormat(private val isV2: Boolean) : MappingsFormat<TsrgMappings> {
    override fun detect(lines: List<String>): Boolean = when {
        isV2 -> lines.firstOrNull()?.startsWith("tsrg2") == true
        lines.size < 2 -> false
        else -> {
            val (fc, fe) = lines
            fc.parts().size == 2 && fe.startsWith('\t') && fe.parts().size == 2
        }
    }

    override fun parse(lines: List<String>): TsrgMappings {
        require(detect(lines)) { "Invalid mappings" }

        val namespaces = if (isV2) (lines.firstOrNull() ?: error("Mappings are empty!")).parts().drop(1)
        else listOf("obf", "srg")

        val mapLines = (if (isV2) lines.drop(1) else lines)
            .dropWhile { it.countStart() != 0 }.filter { it.isNotBlank() }

        var state: TSRGState = MappingState()
        mapLines.forEach { state = state.update(it) }
        repeat(2) { state = state.end() }

        val end = (state as? MappingState ?: error("Did not finish walking tree, parsing failed (ended in $state)"))
        return TsrgMappings(namespaces, end.classes, isV2)
    }

    private sealed interface TSRGState {
        fun update(line: String): TSRGState
        fun end(): TSRGState
    }

    private inner class MappingState : TSRGState {
        val classes = mutableListOf<MappedClass>()

        override fun update(line: String): TSRGState {
            val ident = line.countStart()
            val parts = line.parts()

            require(ident == 0) { "Invalid indent top-level" }
            return ClassState(this, parts)
        }

        override fun end() = this
    }

    private inner class ClassState(val owner: MappingState, val names: List<String>) : TSRGState {
        val comments = mutableListOf<String>()
        val fields = mutableListOf<MappedField>()
        val methods = mutableListOf<MappedMethod>()

        override fun update(line: String): TSRGState {
            val ident = line.countStart()
            if (ident < 1) {
                end()
                return owner.update(line)
            }

            val parts = line.parts()
            return when {
                '(' in parts[1] -> MethodState(this, parts[1], listOf(parts.first()) + parts.drop(2))
                else -> {
                    fields += MappedField(parts, emptyList(), null)
                    this
                }
            }
        }

        override fun end(): TSRGState {
            owner.classes += MappedClass(names, comments, fields, methods)
            return owner.end()
        }
    }

    private inner class MethodState(val owner: ClassState, val desc: String, val names: List<String>) : TSRGState {
        val parameters = mutableListOf<MappedParameter>()

        override fun update(line: String): TSRGState {
            val ident = line.countStart()
            if (ident < 2) {
                end()
                return owner.update(line)
            }

            val parts = line.parts()
            if (parts.size == 1) require(parts.single() == "static") { "Unrecognized method meta: $parts" }
            else parameters += MappedParameter(parts.drop(1), parts.first().toInt())

            return this
        }

        override fun end(): TSRGState {
            owner.methods += MappedMethod(names, emptyList(), desc, parameters, emptyList())
            return owner
        }
    }

    private fun String.countStart(sequence: String = "\t") =
        windowedSequence(sequence.length, sequence.length).takeWhile { it == sequence }.count()

    private fun String.parts() = trimIndent().split(' ')
    private fun List<String>.join() = joinToString(" ")
    private fun List<String>.indent() = map { "\t" + it }

    // I sincerely apologize for this abomination; advent of code worsened my ability to write clean code
    override fun write(mappings: TsrgMappings): List<String> {
        if (!isV2) require(mappings.namespaces.size == 2) {
            "TSRG v1 supports exactly 2 mapping namespaces, found: ${mappings.namespaces}"
        }

        val header = if (isV2) "tsrg2 ${mappings.namespaces.join()}" else null
        return listOfNotNull(header) + mappings.classes.flatMap { c ->
            listOf(c.names.join()) + (c.fields.map { it.names.join() } + c.methods.flatMap { m ->
                listOf((listOf(m.names.first()) + m.desc + m.names.drop(1)).join()) +
                        m.parameters.map { "${it.index} ${it.names.join()}" }.indent()
            }).indent()
        }
    }
}