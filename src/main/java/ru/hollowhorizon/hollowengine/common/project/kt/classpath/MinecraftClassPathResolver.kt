package ru.hollowhorizon.hollowengine.common.project.kt.classpath

import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.classpath

class MinecraftClassPathResolver: ClassPathResolver {
    override val resolverType: String
        get() = "HollowEngine Minecraft ClassPath Resolver"
    override val classpath: Set<ClassPathEntry>
        get() = classpath().map { ClassPathEntry(it.toPath(), null) }.toSet()
}