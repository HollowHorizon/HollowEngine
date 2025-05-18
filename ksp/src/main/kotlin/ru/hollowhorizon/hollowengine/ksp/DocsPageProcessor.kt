package ru.hollowhorizon.hollowengine.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFile
import ru.hollowhorizon.hollowengine.ksp.file.FileProcessor

class DocsPageProcessor : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val files = resolver.getSymbolsWithAnnotation("ru.hollowhorizon.hollowengine.ksp.DocPage")
            .filterIsInstance<KSFile>()

        files.forEach(FileProcessor::process)

        return emptyList()
    }
}

class DocsPageProcessorProvider : SymbolProcessorProvider {
    override fun create(env: SymbolProcessorEnvironment): SymbolProcessor {
        FileProcessor.codeGenerator = env.codeGenerator
        return DocsPageProcessor()
    }
}