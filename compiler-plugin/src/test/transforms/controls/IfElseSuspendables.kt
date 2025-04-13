import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test(first: Boolean) {
    if(first) innerCall1()
    else {
        innerCall2()
    }
}

@Suspendable
fun innerCall1() {
    println("Inner call 1")
}
@Suspendable
fun innerCall2() {
    println("Inner call 2")
}