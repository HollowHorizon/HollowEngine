package ru.hollowhorizon.hollowengine.common.ide.session.definition

import org.benf.cfr.reader.api.CfrDriver
import org.benf.cfr.reader.api.OutputSinkFactory
import org.benf.cfr.reader.api.SinkReturns

internal object CfrSourceDecompiler {
    fun decompile(classFile: String): String? {
        val output = StringBuilder()
        val sinkFactory = object : OutputSinkFactory {
            override fun getSupportedSinks(
                sinkType: OutputSinkFactory.SinkType,
                collectionType: Collection<OutputSinkFactory.SinkClass>,
            ): List<OutputSinkFactory.SinkClass> {
                return listOf(OutputSinkFactory.SinkClass.DECOMPILED, OutputSinkFactory.SinkClass.STRING)
            }

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSink(
                sinkType: OutputSinkFactory.SinkType,
                sinkClass: OutputSinkFactory.SinkClass,
            ): OutputSinkFactory.Sink<T> {
                val sink = OutputSinkFactory.Sink<Any> { value ->
                    when (value) {
                        is SinkReturns.Decompiled -> output.append(value.java)
                        is String -> if (sinkType == OutputSinkFactory.SinkType.JAVA) output.append(value)
                    }
                }
                return sink as OutputSinkFactory.Sink<T>
            }
        }

        CfrDriver.Builder()
            .withOptions(
                mapOf(
                    "silent" to "true",
                    "comments" to "false",
                    "hideutf" to "false",
                )
            )
            .withOutputSink(sinkFactory)
            .build()
            .analyse(listOf(classFile))

        return output.toString().takeIf { it.isNotBlank() }
    }
}
