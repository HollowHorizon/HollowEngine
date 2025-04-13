import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test(branch: Int): Int {
    val result = when(branch) {
        0 -> innerCall("Branch: ", 0)
        1 -> innerCall("Branch: ", 1)
        2 -> innerCall("Branch: ", 2)
        -1 -> innerCall("Branch: ", -1)
        else -> innerCall("Else Branch!", -404)
    }
    println(result)
    return result
}

@Suspendable
fun innerCall(text: String, value: Int): Int {
    println(text+value)
    return value
}