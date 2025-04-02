import com.tschuchort.compiletesting.JvmCompilationResult
import org.benf.cfr.reader.api.CfrDriver
import org.benf.cfr.reader.api.OutputSinkFactory
import org.benf.cfr.reader.api.SinkReturns
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object CfrHelper {
    @OptIn(ExperimentalCompilerApi::class)
    fun decompile(result: JvmCompilationResult) {
        val decompiledCode = ConcurrentHashMap<String, String>()

        println("Decompiled data: ")
        val sinkFactory = object : OutputSinkFactory {
            override fun getSupportedSinks(
                sinkType: OutputSinkFactory.SinkType,
                collection: MutableCollection<OutputSinkFactory.SinkClass>,
            ): MutableList<OutputSinkFactory.SinkClass> {
                return if (sinkType == OutputSinkFactory.SinkType.JAVA && OutputSinkFactory.SinkClass.DECOMPILED in collection) {
                    mutableListOf(
                        OutputSinkFactory.SinkClass.DECOMPILED,
                        OutputSinkFactory.SinkClass.STRING
                    )
                } else {
                    Collections.singletonList(OutputSinkFactory.SinkClass.STRING)
                }
            }

            override fun <T : Any?> getSink(
                p0: OutputSinkFactory.SinkType?,
                p1: OutputSinkFactory.SinkClass?,
            ): OutputSinkFactory.Sink<T> = OutputSinkFactory.Sink {
                (it as? SinkReturns.Decompiled)?.run {
                    decompiledCode[className] = java
                }
            }

        }

        val cfrDriver = CfrDriver.Builder()
            .withOutputSink(sinkFactory)
            .withOptions(mapOf())
            .build()
        cfrDriver.analyse(result.generatedFiles.map { it.absolutePath })

        decompiledCode.forEach { (file, code) ->
            println(file)
            println(code)
        }
    }
}