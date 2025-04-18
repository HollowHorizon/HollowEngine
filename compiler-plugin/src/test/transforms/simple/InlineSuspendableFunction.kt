import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test() {
    innerCall<Suspendable>()
}

@Suspendable
inline fun <reified T> innerCall() {
    innerCall2(T::class.java)
}

@Suspendable
fun innerCall2(type: Class<*>) {
    println("Hello: $type")
}