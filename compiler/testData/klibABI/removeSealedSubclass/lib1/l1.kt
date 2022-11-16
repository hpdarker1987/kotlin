sealed class SC1 {
    class C1 : SC1()
    object O1 : SC1()
    class Removed : SC1()
    class C2 : SC1()
    object O2 : SC1()
}

sealed class SC2 {
    class C1 : SC2()
    object O1 : SC2()
    object Removed : SC2()
    class C2 : SC2()
    object O2 : SC2()
}

sealed interface SI1 {
    class C1 : SI1
    object O1 : SI1
    interface I1 : SI1
    class Removed : SI1
    class C2 : SI1
    object O2 : SI1
    interface I2 : SI1
}

sealed interface SI2 {
    class C1 : SI2
    object O1 : SI2
    interface I1 : SI2
    object Removed : SI2
    class C2 : SI2
    object O2 : SI2
    interface I2 : SI2
}

sealed interface SI3 {
    class C1 : SI3
    object O1 : SI3
    interface I1 : SI3
    interface Removed : SI3
    class C2 : SI3
    object O2 : SI3
    interface I2 : SI3
}
