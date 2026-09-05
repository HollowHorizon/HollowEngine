package ru.hollowhorizon.hollowengine.runtime.remap

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class PayloadRemapTable(
    val from: String,
    val to: String,
    val classes: Map<String, String>,
    val methods: Map<String, String>,
    val fields: Map<String, String>,
) {
    val size: Int get() = classes.size + methods.size + fields.size

    fun write(target: File) {
        target.parentFile?.mkdirs()
        target.outputStream().use(::write)
    }

    fun write(target: OutputStream) {
        GZIPOutputStream(target).bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.appendLine("$HEADER $VERSION")
            writer.appendLine("n $from $to")
            classes.toSortedMap().forEach { (key, value) -> writer.appendLine("c $key $value") }
            methods.toSortedMap().forEach { (key, value) -> writer.appendLine("m $key $value") }
            fields.toSortedMap().forEach { (key, value) -> writer.appendLine("f $key $value") }
        }
    }

    companion object {
        private const val HEADER = "hollowengine-remap-table"
        private const val VERSION = 1

        fun read(source: File): PayloadRemapTable = source.inputStream().use(::read)

        fun read(source: InputStream): PayloadRemapTable =
            GZIPInputStream(source).bufferedReader(StandardCharsets.UTF_8).use(::parse)

        private fun parse(reader: BufferedReader): PayloadRemapTable {
            val header = reader.readLine() ?: error("Remap table is empty")
            require(header == "$HEADER $VERSION") { "Unsupported remap table header: $header" }

            var from: String? = null
            var to: String? = null
            val classes = HashMap<String, String>()
            val methods = HashMap<String, String>()
            val fields = HashMap<String, String>()

            reader.lineSequence().forEach { line ->
                if (line.isEmpty()) return@forEach
                val parts = line.split(' ')
                when (parts[0]) {
                    "n" -> {
                        from = parts[1]
                        to = parts[2]
                    }

                    "c" -> classes[parts[1]] = parts[2]
                    "m" -> methods[parts[1]] = parts[2]
                    "f" -> fields[parts[1]] = parts[2]
                    else -> error("Unknown remap table entry: $line")
                }
            }

            return PayloadRemapTable(
                from = requireNotNull(from) { "Remap table has no namespace declaration" },
                to = requireNotNull(to) { "Remap table has no namespace declaration" },
                classes = classes,
                methods = methods,
                fields = fields,
            )
        }
    }
}
