// TARGET_BACKEND: JVM_IR
// Field VS property: case "lateinit"

// FILE: BaseJava.java
public class BaseJava {
    public String a = "FAIL";
}

// FILE: Derived.kt
class Derived : BaseJava() {
    lateinit var a: String
}

fun box(): String {
    val d = Derived()
    d.a = "OK"
    if ((d as BaseJava).a == "OK") return "FAIL (accidental shadowed field access)"
    return d.a
}
