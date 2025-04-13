import ru.hollowhorizon.hollowengine.scripting.Suspendable
import java.util.Random

@Suspendable
fun test() {
    val rand = Random()
    var value = -1
    while(rand.nextInt(10).apply { value = this } > 1) {
        innerCall(value)
    }
}

@Suspendable
fun innerCall(value: Int) {
    println("Value: $value")
}