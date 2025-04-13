import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test() {
    val lambda = @Suspendable {
        println("Call from lambda")
    }

    println("Call from upper function")
    innerCall(lambda)
}

@Suspendable
fun innerCall(lambda: @Suspendable () -> Unit) {
    println("Call from inner function")
    lambda()
}