package ru.hollowhorizon.hollowengine.ksp.file

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSFile
import kotlinx.serialization.json.Json
import ru.hollowhorizon.hollowengine.ksp.DocPage

object FileProcessor {
    lateinit var codeGenerator: CodeGenerator

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @OptIn(KspExperimental::class)
    fun process(file: KSFile) {
        val page = file.getAnnotationsByType(DocPage::class).first()
        val pageName = page.location.substringAfterLast('/')
        val pageLocation = page.location.substringBeforeLast('/').replace('/', '.')

        val docFile = codeGenerator.createNewFile(
            Dependencies(false),
            "assets.hollowengine.docs.$pageLocation",
            pageName,
            "json"
        )

        docFile.bufferedWriter().use { out ->
            out.append(json.encodeToString(DocFile.fromFile(file, page)))
        }
    }
}