package ru.hollowhorizon.hc.common.utils.toml

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlIndentation
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig

object TomlFormat : Toml(
    inputConfig = TomlInputConfig(
        ignoreUnknownNames = false,
        allowEmptyValues = true,
        allowNullValues = true,
        allowEscapedQuotesInLiteralStrings = true,
        allowEmptyToml = true,
    ),
    outputConfig = TomlOutputConfig(
        indentation = TomlIndentation.FOUR_SPACES,
    )
)