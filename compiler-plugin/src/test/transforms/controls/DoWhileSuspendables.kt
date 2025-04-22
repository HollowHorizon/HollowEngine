import ru.hollowhorizon.hollowengine.scripting.Suspendable
import java.util.Random

@Suspendable
fun test() {
    val rand = Random()
    do {
        var value = rand.nextInt(15)
        innerCall(value)
    } while (value > 1)
}

@Suspendable
fun innerCall(value: Int) {
    println("Value: $value")
}