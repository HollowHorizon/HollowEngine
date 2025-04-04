import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test() {
    val lambda = @Suspendable {

    }

    lambda()
}