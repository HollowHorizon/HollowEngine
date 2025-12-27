package ru.hollowhorizon.gradle.common

import org.gradle.kotlin.dsl.DependencyHandlerScope

interface DependencySetup {
    fun DependencyHandlerScope.setup(minecraftVersion: String)
}