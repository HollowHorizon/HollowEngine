import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test() {
    innerCall()
}

@Suspendable
fun innerCall() {

}