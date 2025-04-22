import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test() {
    innerCall()
    println("Suspend 1 passed")
    innerCall()
    println("Suspend 2 passed")
    innerCall()
    println("Suspend 3 passed")
}

@Suspendable
fun innerCall() {
    println("Inner call called!")
}