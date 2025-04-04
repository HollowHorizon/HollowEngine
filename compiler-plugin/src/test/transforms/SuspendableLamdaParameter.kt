import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test(lambda: @Suspendable (Int) -> Unit) {

    lambda(10)
}