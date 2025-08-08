package ru.hollowhorizon.hollowengine.common.project.kt.formatting

import ru.hollowhorizon.hollowengine.common.project.kt.Configuration
import ru.hollowhorizon.hollowengine.common.project.kt.FormattingConfiguration
import org.eclipse.lsp4j.FormattingOptions as LspFromattingOptions

private const val DEFAULT_INDENT = 4

class FormattingService(private val config: FormattingConfiguration) {

    private val formatter: Formatter get() = when (config.formatter) {
        "ktfmt" -> KtfmtFormatter(config.ktfmt)
        "none" -> NopFormatter
        else -> KtfmtFormatter(config.ktfmt)
    }

    fun formatKotlinCode(
        code: String,
        options: LspFromattingOptions = LspFromattingOptions(DEFAULT_INDENT, true)
    ): String = this.formatter.format(code, options)
}
