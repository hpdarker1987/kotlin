/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.util

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.impl.IrClassImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrConstructorImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrExternalPackageFragmentImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrClassPublicSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrConstructorPublicSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrExternalPackageFragmentSymbolImpl
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isJs

private const val CONSTRUCTOR_NAME = "<init>"
private const val KOTLIN_JS_PACKAGE = "kotlin.js"
private const val JS_EXPORT = "JsExport"
private const val JS_IGNORE = "Ignore"
private const val JS_EXPORT_IGNORE = JS_EXPORT + "." + JS_IGNORE

class IrJsExportUtils(private val platform: TargetPlatform?) {
    private val jsExportIgnoreCtorStub by lazy {
        val parentClassSymbol = IrClassPublicSymbolImpl(IdSignature.CommonSignature(KOTLIN_JS_PACKAGE, JS_EXPORT_IGNORE, null, 0))
        IrConstructorImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            IrDeclarationOrigin.DEFINED,
            IrConstructorPublicSymbolImpl(IdSignature.CommonSignature(KOTLIN_JS_PACKAGE, "$JS_EXPORT_IGNORE.$CONSTRUCTOR_NAME", null, 0)),
            Name.special(CONSTRUCTOR_NAME),
            DescriptorVisibilities.PUBLIC,
            IrSimpleTypeImpl(parentClassSymbol, false, emptyList(), emptyList()),
            isPrimary = true,
            isExpect = false,
            isInline = false,
            isExternal = false
        ).apply {
            parent = IrClassImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                IrDeclarationOrigin.DEFINED,
                parentClassSymbol,
                Name.identifier(JS_IGNORE),
                ClassKind.ANNOTATION_CLASS,
                DescriptorVisibilities.PUBLIC,
                Modality.FINAL
            ).apply {
                parent = IrClassImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    IrDeclarationOrigin.DEFINED,
                    IrClassPublicSymbolImpl(IdSignature.CommonSignature(KOTLIN_JS_PACKAGE, JS_EXPORT_IGNORE, null, 0)),
                    Name.identifier(JS_EXPORT),
                    ClassKind.ANNOTATION_CLASS,
                    DescriptorVisibilities.PUBLIC,
                    Modality.FINAL
                ).apply { parent = IrExternalPackageFragmentImpl(IrExternalPackageFragmentSymbolImpl(), FqName(KOTLIN_JS_PACKAGE)) }
            }
        }
    }

    fun excludeFromJsExport(declaration: IrDeclaration) {
        if (platform.isJs() != true) return
        val jsExportIgnoreCtor = jsExportIgnoreCtorStub
        declaration.annotations += IrConstructorCallImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            jsExportIgnoreCtor.returnType,
            jsExportIgnoreCtor.symbol,
            jsExportIgnoreCtor.typeParameters.size,
            jsExportIgnoreCtor.typeParameters.size,
            jsExportIgnoreCtor.valueParameters.size,
        )
    }
}