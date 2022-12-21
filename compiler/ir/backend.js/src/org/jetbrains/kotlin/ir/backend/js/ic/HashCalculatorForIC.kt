/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.ic

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.CrossModuleReferences
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParametersContainer
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.DumpIrTreeVisitor
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.protobuf.CodedInputStream
import org.jetbrains.kotlin.protobuf.CodedOutputStream
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest

@JvmInline
value class ICHash(private val value: ULong = 0UL) {
    fun combineWith(other: ICHash) = ICHash(value xor (other.value + 0x9e3779b97f4a7c15UL + (value shl 12) + (value shr 4)))

    override fun toString() = value.toString(16)

    fun toProtoStream(out: CodedOutputStream) = out.writeFixed64NoTag(value.toLong())

    companion object {
        fun fromProtoStream(input: CodedInputStream) = ICHash(input.readFixed64().toULong())
    }
}

private class HashCalculatorForIC {
    private val md5 = MessageDigest.getInstance("MD5")

    fun update(data: ByteArray) = md5.update(data)

    fun update(data: Int) = (0..3).forEach { md5.update((data shr (it * 8)).toByte()) }

    fun update(data: String) {
        update(data.length)
        update(data.toByteArray())
    }

    fun update(irElement: IrElement) {
        irElement.accept(
            visitor = DumpIrTreeVisitor(
                out = object : Appendable {
                    override fun append(csq: CharSequence) = this.apply { update(csq.toString().toByteArray()) }
                    override fun append(csq: CharSequence, start: Int, end: Int) = append(csq.subSequence(start, end))
                    override fun append(c: Char) = append(c.toString())
                }
            ), data = ""
        )
    }

    fun updateAnnotationContainer(annotationContainer: IrAnnotationContainer) {
        updateForEach(annotationContainer.annotations, ::update)
    }

    inline fun <T> updateForEach(collection: Collection<T>, f: (T) -> Unit) {
        update(collection.size)
        collection.forEach { f(it) }
    }

    private fun bytesToULong(d: ByteArray, offset: Int): ULong {
        var hash = 0UL
        repeat(8) {
            hash = hash or ((d[it + offset].toULong() and 0xFFUL) shl (it * 8))
        }
        return hash
    }

    fun outputStream(): OutputStream {
        return object : OutputStream() {
            override fun write(b: Int) = update(b)
            override fun write(b: ByteArray, off: Int, len: Int) = md5.update(b, off, len)
            override fun write(b: ByteArray) = md5.update(b)
        }
    }

    fun finalize(): ICHash {
        val d = md5.digest()
        md5.reset()
        return ICHash(bytesToULong(d, 0)).combineWith(ICHash(bytesToULong(d, 8)))
    }
}

internal class ICHasher {
    private val hashCalculator = HashCalculatorForIC()

    fun calculateStringHash(data: String): ICHash {
        hashCalculator.update(data)
        return hashCalculator.finalize()
    }

    fun calculateConfigHash(config: CompilerConfiguration): ICHash {
        val importantSettings = listOf(
            JSConfigurationKeys.GENERATE_DTS,
            JSConfigurationKeys.MODULE_KIND,
            JSConfigurationKeys.PROPERTY_LAZY_INITIALIZATION
        )
        hashCalculator.updateForEach(importantSettings) { key ->
            hashCalculator.update(key.toString())
            hashCalculator.update(config.get(key).toString())
        }

        hashCalculator.update(config.languageVersionSettings.toString())
        return hashCalculator.finalize()
    }

    fun calculateIrFunctionHash(function: IrFunction): ICHash {
        hashCalculator.update(function)
        return hashCalculator.finalize()
    }

    fun calculateIrAnnotationContainerHash(container: IrAnnotationContainer): ICHash {
        hashCalculator.updateAnnotationContainer(container)
        return hashCalculator.finalize()
    }

    fun calculateIrSymbolHash(symbol: IrSymbol): ICHash {
        hashCalculator.update(symbol.toString())
        // symbol rendering prints very little information about type parameters
        // TODO may be it make sense to update rendering?
        (symbol.owner as? IrTypeParametersContainer)?.let { typeParameters ->
            hashCalculator.updateForEach(typeParameters.typeParameters) { typeParameter ->
                hashCalculator.update(typeParameter.symbol.toString())
            }
        }
        (symbol.owner as? IrFunction)?.let { irFunction ->
            hashCalculator.updateForEach(irFunction.valueParameters) { functionParam ->
                // symbol rendering doesn't print default params information
                // it is important to understand if default params were added or removed
                hashCalculator.update(functionParam.defaultValue?.let { 1 } ?: 0)
            }
        }
        (symbol.owner as? IrAnnotationContainer)?.let(hashCalculator::updateAnnotationContainer)
        return hashCalculator.finalize()
    }

    fun calculateLibrarySrcFileHash(lib: KotlinLibrary, fileIndex: Int): ICHash {
        hashCalculator.update(lib.types(fileIndex))
        hashCalculator.update(lib.signatures(fileIndex))
        hashCalculator.update(lib.strings(fileIndex))
        hashCalculator.update(lib.declarations(fileIndex))
        hashCalculator.update(lib.bodies(fileIndex))
        return hashCalculator.finalize()
    }

    fun calculateFileHash(file: File): ICHash = FileHashCalculatorForIC(file, hashCalculator).icHash

    private class FileHashCalculatorForIC(private val file: File, private val hashCalculator: HashCalculatorForIC) {
        val icHash = run {
            file.update("")
            hashCalculator.finalize()
        }

        private fun File.update(prefix: String) {
            if (isDirectory) {
                updateDir(prefix)
            } else {
                updateRegularFile()
            }
        }

        private fun File.updateDir(prefix: String) {
            listFiles()!!.sortedBy { it.name }.forEach { f ->
                val filePrefix = "$prefix${f.name}/"
                hashCalculator.update(filePrefix)
                f.update(filePrefix)
            }
        }

        private fun File.updateRegularFile() {
            inputStream().use { stream -> stream.copyTo(hashCalculator.outputStream()) }
        }
    }
}

internal fun CrossModuleReferences.crossModuleReferencesHashForIC() = HashCalculatorForIC().apply {
    update(moduleKind.ordinal)

    updateForEach(importedModules) { importedModule ->
        update(importedModule.externalName)
        update(importedModule.internalName.toString())
        update(importedModule.relativeRequirePath ?: "")
    }

    updateForEach(transitiveJsExportFrom) { transitiveExport ->
        update(transitiveExport.internalName.toString())
        update(transitiveExport.externalName)
    }

    updateForEach(exports.keys.sorted()) { tag ->
        update(tag)
        update(exports[tag]!!)
    }

    updateForEach(imports.keys.sorted()) { tag ->
        val import = imports[tag]!!
        update(tag)
        update(import.exportedAs)
        update(import.moduleExporter.toString())
    }
}.finalize()
