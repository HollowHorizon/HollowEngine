import ru.hollowhorizon.hollowengine.scripting.Suspendable
import ru.hollowhorizon.hollowengine.compiler.coroutine.async

@Suspendable
fun test() {
    val async = async {
        println("Внутри async")
        example()
    }

    println("Async не запущен")
    async.start()
    println("Async запущен")
    async.await()
    println("Async завершён")
}

@Suspendable
fun example() {
    println("Внутри функции")
}