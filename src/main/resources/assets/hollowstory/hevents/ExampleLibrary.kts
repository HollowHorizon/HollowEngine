fun someFunc(name: String) {
    println("Hello, name")
}

class Test {
    fun test() {
        someFunc("test")
    }

    val test2 = {
        someFunc("test2")
    }
}