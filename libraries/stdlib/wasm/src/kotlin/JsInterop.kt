/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.wasm

/**
 * When applied to parameter or return types of an external function, forces them not to use JS interop adapters.
 * This is a temporary annotation because K/Wasm <-> JS interop is not designed yet.
 */
@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
public annotation class WasmInterop()