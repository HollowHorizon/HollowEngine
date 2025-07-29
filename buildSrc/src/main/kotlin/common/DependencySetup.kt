package common

import org.gradle.kotlin.dsl.DependencyHandlerScope

interface DependencySetup {
    fun DependencyHandlerScope.setup(minecraftVersion: String)
}