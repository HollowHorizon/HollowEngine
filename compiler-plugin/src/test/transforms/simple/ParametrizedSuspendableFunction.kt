import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun test(name: String, email: String) {
    innerCall()
    println("Name: $name")
    innerCall()
    println("Email: $email")
}

@Suspendable
fun innerCall() {
    println("<Inner call>")
}