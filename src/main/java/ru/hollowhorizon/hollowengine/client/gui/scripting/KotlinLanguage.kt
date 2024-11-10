package ru.hollowhorizon.hollowengine.client.gui.scripting

import imgui.extension.texteditor.TextEditorLanguageDefinition

val KotlinLanguage = TextEditorLanguageDefinition.C().apply {
    preprocChar = '@'
    setKeywords(
        arrayOf(
            "break", "continue", "switch", "case", "try",
            "catch", "delete", "do", "while", "else", "finally", "if",
            "else", "for", "is", "as", "in", "instanceof",
            "new", "throw", "typeof", "typealias", "with", "yield", "when", "return",
            "by", "constructor", "delegate", "dynamic", "field", "get", "set", "init", "value",
            "where", "actual", "annotation", "companion", "field", "external", "infix", "inline", "inner", "internal",
            "open", "operator", "out", "override", "suspend", "vararg",
            "abstract", "extends", "final", "implements", "interface", "super", "throws",
            "data", "class", "fun", "var", "val", "import", "Java", "JSON", "void", "uniform", "using",
            "const", "uint", "float", "int", "double", "vec2", "vec3", "vec4", "sampler2D", "ifdef", "endif",
            "default", "true", "false", "package"
        )
    )

    name = "KotlinScript"
    singleLineComment = "//"
    commentStart = "/*"
    commentEnd = "*/"
    autoIndentation = true
}
