package ru.hollowhorizon.hollowengine.common.addons

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class HollowAddonActivationStore(private val file: File) {
    fun load(): Set<String> {
        if (!file.isFile) return emptySet()
        return file.readLines(StandardCharsets.UTF_8)
            .asSequence()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
            .toCollection(LinkedHashSet())
    }

    fun save(disabledAddonIds: Set<String>) {
        file.parentFile.mkdirs()
        val temporaryFile = file.resolveSibling(file.name + ".tmp")
        val contents = buildString {
            appendLine("# One disabled HollowEngine addon id per line.")
            disabledAddonIds.sorted().forEach(::appendLine)
        }
        Files.writeString(temporaryFile.toPath(), contents, StandardCharsets.UTF_8)
        runCatching {
            Files.move(
                temporaryFile.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
