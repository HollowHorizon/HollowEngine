import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test() {
    "Hello".innerCall<Suspendable>()
}

@Suspendable
inline fun <reified T> String.innerCall() {
    innerCall2(this, T::class.java)
}


@Suspendable
fun innerCall2(text: String, type: Class<*>) {
    println("$text: $type")
}