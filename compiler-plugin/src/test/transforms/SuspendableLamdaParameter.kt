import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test(lambda: @Suspendable (Int) -> String) {
    val name = lambda(10)

    println("Result: ${name.startsWith("test")}")
}