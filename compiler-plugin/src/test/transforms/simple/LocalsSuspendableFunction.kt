import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test() {
    val unusued = "Compiler must ignore this."
    val before = 10
    println(unusued)
    innerCall()
    val after = "There is nothing"
    innerCall()
    println("Before: $before")
    println("After: $after")
}

@Suspendable
fun innerCall() {

}