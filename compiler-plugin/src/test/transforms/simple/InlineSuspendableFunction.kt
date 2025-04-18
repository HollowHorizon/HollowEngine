import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test() {
    innerCall<Suspendable>("Hello")
}

@Suspendable
inline fun <reified T> innerCall(text: String) {
    innerCall2(text, T::class.java)
}

@Suspendable
inline fun <reified T> await(value: T): T {
    val listener = value
    while(listener == null);
    return listener as T
}


@Suspendable
fun innerCall2(text: String, type: Class<*>) {
    println("$text: $type")
}