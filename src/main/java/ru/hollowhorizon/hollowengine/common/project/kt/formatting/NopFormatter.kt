package ru.hollowhorizon.hollowengine.common.project.kt.formatting

import org.eclipse.lsp4j.FormattingOptions as LspFormattingOptions

object NopFormatter : Formatter {
    override fun format(code: String, options: LspFormattingOptions): String = code
}

