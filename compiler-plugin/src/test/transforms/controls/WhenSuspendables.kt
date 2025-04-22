import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test(branch: Int) {
    when(branch) {
        0 -> innerCall("Branch: 0")
        1 -> innerCall("Branch: 1")
        2 -> innerCall("Branch: 2")
        -1 -> innerCall("Branch: -1")
        else -> innerCall("Else Branch!")
    }
}

@Suspendable
fun innerCall(text: String) {
    println(text)
}