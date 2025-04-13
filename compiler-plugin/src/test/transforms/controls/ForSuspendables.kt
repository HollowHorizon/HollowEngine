import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test() {
    for(i in 0..10) {
        innerCall(i)
    }
}

@Suspendable
fun innerCall(value: Int) {
    println("Value: $value")
}