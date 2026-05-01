package ru.hollowhorizon.hollowengine.katari.processor

internal class KatariBindingsJsonGen(
    private val scriptTypes: List<ScriptTypeModel>,
    private val functions: List<FunctionModel>,
    private val classes: List<ClassModel>,
    private val properties: List<PropertyModel>,
) {
    fun generate(): String = buildString {
        appendLine("{")
        appendLine("  \"globals\": {")
        appendLine("    \"functions\": [")
        val globalFunctions = functions.filter { it.receiver == null }
        globalFunctions.forEachIndexed { i, f ->
            appendFunction(f, indent = "      ")
            if (i < globalFunctions.lastIndex) append(",")
            appendLine()
        }
        appendLine("    ],")
        appendLine("    \"properties\": [")
        val globalProps = properties.filter { true } // top-level extension properties
        globalProps.forEachIndexed { i, p ->
            appendProperty(p, indent = "      ")
            if (i < globalProps.lastIndex) append(",")
            appendLine()
        }
        appendLine("    ]")
        appendLine("  },")
        appendLine("  \"types\": {")

        // собираем всё по типам
        val typeMap = mutableMapOf<String, MutableList<Any>>()
        functions.filter { it.receiver != null }.forEach { f ->
            typeMap.getOrPut(f.receiver!!.hostTypeId ?: "Unknown") { mutableListOf() }.add(f)
        }
        classes.forEach { cls ->
            val id = cls.type.typeId
            typeMap.getOrPut(id) { mutableListOf() }.addAll(cls.functions)
            typeMap.getOrPut(id) { mutableListOf() }.addAll(cls.constructors)
            typeMap.getOrPut(id) { mutableListOf() }.addAll(cls.properties)
        }
        properties.forEach { p ->
            typeMap.getOrPut(p.receiver.hostTypeId ?: "Unknown") { mutableListOf() }.add(p)
        }

        val typeEntries = typeMap.entries.toList()
        typeEntries.forEachIndexed { ti, (typeId, items) ->
            appendLine("    \"$typeId\": {")
            appendLine("      \"id\": \"$typeId\",")

            val typeFunctions = items.filterIsInstance<FunctionModel>()
            val typeProperties = items.filterIsInstance<PropertyModel>()

            appendLine("      \"functions\": [")
            typeFunctions.forEachIndexed { i, f ->
                appendFunction(f, indent = "        ")
                if (i < typeFunctions.lastIndex) append(",")
                appendLine()
            }
            appendLine("      ],")
            appendLine("      \"properties\": [")
            typeProperties.forEachIndexed { i, p ->
                appendProperty(p, indent = "        ")
                if (i < typeProperties.lastIndex) append(",")
                appendLine()
            }
            appendLine("      ]")
            append("    }")
            if (ti < typeEntries.lastIndex) append(",")
            appendLine()
        }

        appendLine("  }")
        append("}")
    }

    private fun StringBuilder.appendFunction(f: FunctionModel, indent: String) {
        append("$indent{")
        append("\"name\": \"${f.scriptName}\", ")
        append("\"suspend\": ${f.isSuspend}, ")
        append("\"returns\": \"${f.returnType.kotlinType.substringAfterLast('.')}\", ")
        append("\"params\": [")
        f.parameters.forEachIndexed { i, p ->
            append("{\"name\": \"${p.name}\", \"type\": \"${p.type.kotlinType.substringAfterLast('.')}\", \"optional\": ${p.hasDefault}}")
            if (i < f.parameters.lastIndex) append(", ")
        }
        append("]}")
    }

    private fun StringBuilder.appendProperty(p: PropertyModel, indent: String) {
        append("$indent{")
        append("\"name\": \"${p.scriptName}\", ")
        append("\"type\": \"${p.valueType.kotlinType.substringAfterLast('.')}\", ")
        append("\"mutable\": ${p.writable}}")
    }
}