import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test() {
    for (i in 0..10) {
        innerCall(i)
    }

    (0..10).filter { it % 2 == 0 }
        .forEach {
            innerCall(it)
        }
}

@Suspendable
fun innerCall(value: Int) {
    println("Value: $value")
}