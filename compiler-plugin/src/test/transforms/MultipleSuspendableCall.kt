import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test() {
    println(innerCall() + " | " + innerCall())
}

@Suspendable
fun innerCall(): String {
    return "Hello world!"
}