package ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable

fun main() {
    for (i in 0..22) {
        val params: String
        val args: String
        if(i == 0) {
            params = "out R"
            args = ""
        } else {
            params = (1..i).joinToString(separator = ", ") { "in P$it" } + ", out R"
            args = (1..i).joinToString(separator = ", ") { "p$it: P$it" }
        }

        println(
            """
            interface SFunction$i<$params> : Function<R> {
                /** Invokes the function with the specified argument. */
                @Suspendable
                operator fun invoke($args): R
                fun restoreState($args)
                fun updateAsyncs($args)
                val serializer: KSerializer<*>
            }
        """.trimIndent()
        )
    }
}