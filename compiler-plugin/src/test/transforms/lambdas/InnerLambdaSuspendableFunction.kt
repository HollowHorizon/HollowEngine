import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test() {
    val data = 1

    val lambda = @Suspendable {
        println("Call from lambda")

        val next = @Suspendable {
            println("Call from next")

            innerCall(data)
        }

        next()
    }

    println("Call from upper function")
    lambda()
}

@Suspendable
fun innerCall(i: Int) {
    println("Call from inner function ($i)")
}