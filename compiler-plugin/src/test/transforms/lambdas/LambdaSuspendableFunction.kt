import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test() {
    val lambda = @Suspendable {
        println("Call from lambda")
        innerCall()
    }

    println("Call from upper function")
    lambda()
}

@Suspendable
fun innerCall() {
    println("Call from inner function")
}