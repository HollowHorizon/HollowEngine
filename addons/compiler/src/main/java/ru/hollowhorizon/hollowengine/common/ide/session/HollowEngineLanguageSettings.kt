package ru.hollowhorizon.hollowengine.common.ide.session

import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl

object HollowEngineLanguageSettings {
    val INSTANCE = LanguageVersionSettingsImpl(
        LanguageVersion.LATEST_STABLE, ApiVersion.LATEST_STABLE,
        emptyMap(),
        mapOf(
            LanguageFeature.CollectionLiterals to LanguageFeature.State.ENABLED
        )
    )
}