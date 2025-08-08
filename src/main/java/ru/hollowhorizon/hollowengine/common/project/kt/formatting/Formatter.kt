package ru.hollowhorizon.hollowengine.common.project.kt.formatting

import org.eclipse.lsp4j.FormattingOptions as LspFromattingOptions

interface Formatter {
    fun format(code: String, options: LspFromattingOptions): String
}

