import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test(name: String): String {
    val prefix = "Hello,"
    val lambda = @Suspendable {
        "$prefix User: $name"
    }

    println("Call from upper function")
    return lambda()
}