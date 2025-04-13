import ru.hollowhorizon.hollowengine.scripting.Suspendable
import ru.hollowhorizon.hollowengine.compiler.coroutine.async

@Suspendable
fun test() {
    val async = async {
        example()
    }
}

@Suspendable
fun example() {

}