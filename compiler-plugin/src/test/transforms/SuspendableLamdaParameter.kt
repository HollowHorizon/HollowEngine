import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test(lambda: @Suspendable (Int) -> String) {
    println("Result: ${lambda(10)}")
}