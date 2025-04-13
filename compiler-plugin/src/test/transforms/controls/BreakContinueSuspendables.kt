import ru.hollowhorizon.hollowengine.scripting.Suspendable
import java.util.Random

@Suspendable
fun test() {
    val rand = Random()
    var value = -1
    while(true) {
        value = rand.nextInt(60)
        if(value == 50) {
            println("Break")
            break
        }
        if(value == 51) {
            println("Contine")
            continue
        }

        innerCall(value)
    }
}

@Suspendable
fun innerCall(value: Int) {
    println("Value: $value")
}