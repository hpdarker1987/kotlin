// !OPT_IN: kotlin.ExperimentalStdlibApi
// IGNORE_BACKEND: JVM

fun testChar(a: Char, x: Char, y: Char) = a in x..<y

fun testByte(a: Byte, x: Byte, y: Byte) = a in x..<y

fun testShort(a: Short, x: Short, y: Short) = a in x..<y

fun testInt(a: Int, x: Int, y: Int) = a in x..<y

fun testLong(a: Long, x: Long, y: Long) = a in x..<y

// 0 until
// 0 INVOKEVIRTUAL
// 0 contains
