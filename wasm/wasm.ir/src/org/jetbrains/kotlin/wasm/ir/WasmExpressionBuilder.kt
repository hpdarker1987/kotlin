/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.wasm.ir

import org.jetbrains.kotlin.wasm.ir.source.location.SourceLocation

abstract class WasmExpressionBuilder {
    abstract fun buildInstr(op: WasmOp, location: SourceLocation, vararg immediates: WasmImmediate)

    fun buildInstr(op: WasmOp, vararg immediates: WasmImmediate) {
        buildInstr(op, SourceLocation.TBDLocation, *immediates)
    }

    abstract var numberOfNestedBlocks: Int

    fun buildConstI32(value: Int, location: SourceLocation) {
        buildInstr(WasmOp.I32_CONST, location, WasmImmediate.ConstI32(value))
    }

    fun buildConstI64(value: Long, location: SourceLocation) {
        buildInstr(WasmOp.I64_CONST, location, WasmImmediate.ConstI64(value))
    }

    fun buildConstF32(value: Float, location: SourceLocation) {
        buildInstr(WasmOp.F32_CONST, location, WasmImmediate.ConstF32(value.toRawBits().toUInt()))
    }

    fun buildConstF64(value: Double, location: SourceLocation) {
        buildInstr(WasmOp.F64_CONST, location, WasmImmediate.ConstF64(value.toRawBits().toULong()))
    }

    fun buildConstI32Symbol(value: WasmSymbol<Int>, location: SourceLocation) {
        buildInstr(WasmOp.I32_CONST, location, WasmImmediate.SymbolI32(value))
    }

    fun buildUnreachable(location: SourceLocation) {
        buildInstr(WasmOp.UNREACHABLE, location)
    }

    @Suppress("UNUSED_PARAMETER")
    inline fun buildBlock(label: String?, resultType: WasmType? = null, body: (Int) -> Unit) {
        numberOfNestedBlocks++
        buildInstr(WasmOp.BLOCK, WasmImmediate.BlockType.Value(resultType))
        body(numberOfNestedBlocks)
        buildEnd()
    }

    @Suppress("UNUSED_PARAMETER")
    inline fun buildLoop(label: String?, resultType: WasmType? = null, body: (Int) -> Unit) {
        numberOfNestedBlocks++
        buildInstr(WasmOp.LOOP, WasmImmediate.BlockType.Value(resultType))
        body(numberOfNestedBlocks)
        buildEnd()
    }

    @Suppress("UNUSED_PARAMETER")
    fun buildIf(label: String?, resultType: WasmType? = null) {
        numberOfNestedBlocks++
        buildInstr(WasmOp.IF, WasmImmediate.BlockType.Value(resultType))
    }

    fun buildElse() {
        buildInstr(WasmOp.ELSE)
    }

    fun buildBlock(resultType: WasmType? = null): Int {
        numberOfNestedBlocks++
        buildInstr(WasmOp.BLOCK, WasmImmediate.BlockType.Value(resultType))
        return numberOfNestedBlocks
    }

    fun buildEnd() {
        numberOfNestedBlocks--
        buildInstr(WasmOp.END)
    }


    fun buildBrInstr(brOp: WasmOp, absoluteBlockLevel: Int) {
        val relativeLevel = numberOfNestedBlocks - absoluteBlockLevel
        assert(relativeLevel >= 0) { "Negative relative block index" }
        buildInstr(brOp, WasmImmediate.LabelIdx(relativeLevel))
    }

    fun buildBrInstr(brOp: WasmOp, absoluteBlockLevel: Int, symbol: WasmSymbolReadOnly<WasmTypeDeclaration>) {
        val relativeLevel = numberOfNestedBlocks - absoluteBlockLevel
        assert(relativeLevel >= 0) { "Negative relative block index" }
        buildInstr(brOp, WasmImmediate.LabelIdx(relativeLevel), WasmImmediate.TypeIdx(symbol))
    }

    fun buildBr(absoluteBlockLevel: Int) {
        buildBrInstr(WasmOp.BR, absoluteBlockLevel)
    }

    fun buildThrow(tagIdx: Int) {
        buildInstr(WasmOp.THROW, WasmImmediate.TagIdx(tagIdx))
    }

    @Suppress("UNUSED_PARAMETER")
    fun buildTry(label: String?, resultType: WasmType? = null) {
        numberOfNestedBlocks++
        buildInstr(WasmOp.TRY, WasmImmediate.BlockType.Value(resultType))
    }

    fun buildCatch(tagIdx: Int) {
        buildInstr(WasmOp.CATCH, WasmImmediate.TagIdx(tagIdx))
    }

    fun buildBrIf(absoluteBlockLevel: Int) {
        buildBrInstr(WasmOp.BR_IF, absoluteBlockLevel)
    }

    fun buildCall(symbol: WasmSymbol<WasmFunction>, location: SourceLocation) {
        buildInstr(WasmOp.CALL, location, WasmImmediate.FuncIdx(symbol))
    }

    fun buildCallIndirect(
        symbol: WasmSymbol<WasmFunctionType>,
        tableIdx: WasmSymbolReadOnly<Int> = WasmSymbol(0),
        location: SourceLocation
    ) {
        buildInstr(
            WasmOp.CALL_INDIRECT,
            location,
            WasmImmediate.TypeIdx(symbol),
            WasmImmediate.TableIdx(tableIdx)
        )
    }

    fun buildGetLocal(local: WasmLocal, location: SourceLocation) {
        buildInstr(WasmOp.LOCAL_GET, location, WasmImmediate.LocalIdx(local))
    }

    fun buildSetLocal(local: WasmLocal, location: SourceLocation) {
        buildInstr(WasmOp.LOCAL_SET, location, WasmImmediate.LocalIdx(local))
    }

    fun buildGetGlobal(global: WasmSymbol<WasmGlobal>, location: SourceLocation) {
        buildInstr(WasmOp.GLOBAL_GET, location, WasmImmediate.GlobalIdx(global))
    }

    fun buildSetGlobal(global: WasmSymbol<WasmGlobal>, location: SourceLocation) {
        buildInstr(WasmOp.GLOBAL_SET, location, WasmImmediate.GlobalIdx(global))
    }

    fun buildStructGet(struct: WasmSymbol<WasmTypeDeclaration>, fieldId: WasmSymbol<Int>, location: SourceLocation) {
        buildInstr(
            WasmOp.STRUCT_GET,
            location,
            WasmImmediate.GcType(struct),
            WasmImmediate.StructFieldIdx(fieldId)
        )
    }

    fun buildStructNew(struct: WasmSymbol<WasmTypeDeclaration>, location: SourceLocation) {
        buildInstr(WasmOp.STRUCT_NEW, location, WasmImmediate.GcType(struct))
    }

    fun buildStructSet(struct: WasmSymbol<WasmTypeDeclaration>, fieldId: WasmSymbol<Int>, location: SourceLocation) {
        buildInstr(
            WasmOp.STRUCT_SET,
            location,
            WasmImmediate.GcType(struct),
            WasmImmediate.StructFieldIdx(fieldId)
        )
    }

    fun buildRefCastNullStatic(toType: WasmSymbolReadOnly<WasmTypeDeclaration>, location: SourceLocation) {
        buildInstr(WasmOp.REF_CAST_DEPRECATED, location, WasmImmediate.TypeIdx(toType))
    }

    fun buildRefTestStatic(toType: WasmSymbolReadOnly<WasmTypeDeclaration>, location: SourceLocation) {
        buildInstr(WasmOp.REF_TEST_DEPRECATED, location, WasmImmediate.TypeIdx(toType))
    }

    fun buildRefNull(type: WasmHeapType, location: SourceLocation) {
        buildInstr(WasmOp.REF_NULL, location, WasmImmediate.HeapType(WasmRefType(type)))
    }

    fun buildDrop(location: SourceLocation) {
        buildInstr(WasmOp.DROP, location)
    }
}

fun WasmExpressionBuilder.buildUnreachableForVerifier() {
    buildUnreachable(SourceLocation.NoLocation("This instruction should never be reached, but required for wasm verifier"))
}

fun WasmExpressionBuilder.buildUnreachableAfterNothingType() {
    buildUnreachable(
        SourceLocation.NoLocation(
            "The unreachable instruction after an expression with Nothing type to make sure that " +
                    "execution doesn't come here (or it fails fast if so). It also might be required for wasm verifier."
        )
    )
}
