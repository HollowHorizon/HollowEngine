import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test() {
    innerCall("Hello")
    innerCall(42)
}

@Suspendable
fun innerCall(name: String) {
    println(name)
}

@Suspendable
fun innerCall(number: Int) {
    println(number)
}