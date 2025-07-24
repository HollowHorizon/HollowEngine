package ru.hollowhorizon.hollowengine.common.project.kt.formatting

import ru.hollowhorizon.hollowengine.common.project.kt.KtfmtConfiguration
import com.facebook.ktfmt.format.Formatter as Ktfmt
import org.eclipse.lsp4j.FormattingOptions as LspFormattingOptions

class KtfmtFormatter(private val config: KtfmtConfiguration) : Formatter {
    override fun format(
        code: String,
        options: LspFormattingOptions,
    ): String {
        val style = when (config.style) {
            "google" -> Ktfmt.GOOGLE_FORMAT
            "facebook" -> Ktfmt.META_FORMAT
            else -> Ktfmt.KOTLINLANG_FORMAT
        }

        return Ktfmt.format(style, code)
    }
}

