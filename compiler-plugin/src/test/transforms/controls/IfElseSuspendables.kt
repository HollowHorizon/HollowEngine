import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test(first: Boolean) {
    if(first) innerCall1()
    else {
        if(!first) println("Hi")
        innerCall2()
    }

    if(first) innerCall2()
    if(first) innerCall2()
    if(!first) innerCall2()

    if(first && !first) {
        if(first || !first) println("How")
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