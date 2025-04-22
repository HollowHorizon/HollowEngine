import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test(isFirst: Boolean): String {
    if(isFirst) return "First"
    innerCall()
    return "Second"
}

@Suspendable
fun innerCall() {
}