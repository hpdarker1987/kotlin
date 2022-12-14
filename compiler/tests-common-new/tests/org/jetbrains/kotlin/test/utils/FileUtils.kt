/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.utils

import org.jetbrains.kotlin.test.directives.model.Directive
import java.io.File

private const val KT = ".kt"
private const val KTS = ".kts"

// Prefixes are chosen such that LL FIR test data cannot be mistaken for FIR test data.
private const val FIR_PREFIX = ".fir"
private const val LL_FIR_PREFIX = ".ll"

val CUSTOM_TEST_DATA_EXTENSION_PATTERN = "^(.+)\\.(fir|ll)\\.kts?\$"

val File.isFirTestData: Boolean
    get() = isCustomTestDataWithPrefix(FIR_PREFIX)

/**
 * @see File.llFirTestDataFile
 */
val File.isLLFirTestData: Boolean
    get() = isCustomTestDataWithPrefix(LL_FIR_PREFIX)

val File.isCustomTestData: Boolean
    get() = isFirTestData || isLLFirTestData

private fun File.isCustomTestDataWithPrefix(prefix: String): Boolean =
    name.endsWith("$prefix$KT") || name.endsWith("$prefix$KTS")

val File.firTestDataFile: File
    get() = getCustomTestDataFileWithPrefix(FIR_PREFIX)

/**
 * An LL FIR test data file (`.ll.kt`) allows tailoring the expected output of a test to the LL FIR case. In very rare cases, LL FIR may
 * legally diverge from the output of the K2 compiler, such as when the compiler's error behavior is deliberately unspecified. (For an
 * example, see `kotlinJavaKotlinCycle.ll.kt`.)
 */
val File.llFirTestDataFile: File
    get() = getCustomTestDataFileWithPrefix(LL_FIR_PREFIX)

private fun File.getCustomTestDataFileWithPrefix(prefix: String): File =
    if (isCustomTestDataWithPrefix(prefix)) this
    else {
        // Because `File` can be `.ll.kt` or `.fir.kt` test data, we have to go off `originalTestDataFileName`, which removes the prefix
        // intelligently.
        val originalName = originalTestDataFileName
        val customName =
            if (originalName.endsWith(KTS)) "${originalName.removeSuffix(KTS)}$prefix$KTS"
            else "${originalName.removeSuffix(KT)}$prefix$KT"
        parentFile.resolve(customName)
    }

val File.originalTestDataFile: File
    get() {
        val originalName = originalTestDataFileName
        return if (originalName != name) parentFile.resolve(originalName) else this
    }

val File.originalTestDataFileName: String
    get() =
        if (isLLFirTestData) getOriginalTestDataFileNameFromPrefix(LL_FIR_PREFIX)
        else if (isFirTestData) getOriginalTestDataFileNameFromPrefix(FIR_PREFIX)
        else name

private fun File.getOriginalTestDataFileNameFromPrefix(prefix: String): String =
    if (name.endsWith(KTS)) "${name.removeSuffix("$prefix$KTS")}$KTS"
    else "${name.removeSuffix("$prefix$KT")}$KT"

fun File.withExtension(extension: String): File {
    return withSuffixAndExtension(suffix = "", extension)
}

fun File.withSuffixAndExtension(suffix: String, extension: String): File {
    @Suppress("NAME_SHADOWING")
    val extension = extension.removePrefix(".")
    return parentFile.resolve("$nameWithoutExtension$suffix.$extension")
}

/*
 * Please use this method only in places where `TestModule` is not accessible
 * In other cases use testModule.directives
 */
fun File.isDirectiveDefined(directive: String): Boolean = this.useLines { line ->
    line.any { it == directive }
}

fun File.removeDirectiveFromFile(directive: Directive) {
    val directiveName = directive.name
    val directiveRegexp = "^// $directiveName(:.*)?$(\n)?".toRegex(RegexOption.MULTILINE)
    val text = readText()
    val directiveRange = directiveRegexp.find(text)?.range
        ?: error("Directive $directiveName was not found in $this")
    val textWithoutDirective = text.removeRange(directiveRange)
    writeText(textWithoutDirective)
}