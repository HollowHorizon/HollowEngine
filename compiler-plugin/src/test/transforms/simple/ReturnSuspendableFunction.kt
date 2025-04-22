import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test(): String {
    val result = innerCall()
    return "Result: $result"
}

@Suspendable
fun innerCall(): Int {
    return 1
}