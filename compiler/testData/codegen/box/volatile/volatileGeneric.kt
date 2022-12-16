// WITH_STDLIB
// IGNORE_BACKEND: WASM

import kotlin.jvm.Volatile

class GenericWrapper<T>(@Volatile var x: T)

val global = GenericWrapper("FA")
val globalLong = GenericWrapper(1L)

fun box() : String {
    val local = GenericWrapper("IL")
    val localLong = GenericWrapper(2L)
    if (global.x + local.x != "FAIL") return "FAIL 1"
    if (globalLong.x + localLong.x != 3L) return "FAIL 2"
    global.x = "O"
    local.x = "K"
    globalLong.x = 5L
    localLong.x = 6L
    if (globalLong.x + localLong.x != 11L) return "FAIL 3"
    return global.x + local.x
}