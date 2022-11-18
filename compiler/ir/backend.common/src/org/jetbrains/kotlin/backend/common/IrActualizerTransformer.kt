/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common

import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionReferenceImpl
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

class IrActualizerTransformer(private val expectActualMap: Map<IrSymbol, IrSymbol>) : IrElementTransformerVoid() {
    override fun visitFunction(declaration: IrFunction): IrStatement {
        return (visitDeclaration(declaration) as IrFunction).also {
            it.valueParameters.forEach { parameter ->
                parameter.type = parameter.type.actualizeIfNeeded()
            }
            it.returnType = it.returnType.actualizeIfNeeded()
        }
    }

    override fun visitCallableReference(expression: IrCallableReference<*>): IrExpression {
        return (visitMemberAccess(expression) as IrCallableReference<*>).also {
            it.type = it.type.actualizeIfNeeded()
        }
    }

    override fun visitFunctionReference(expression: IrFunctionReference): IrExpression {
        return IrFunctionReferenceImpl(
            expression.startOffset,
            expression.endOffset,
            expression.type.actualizeIfNeeded(),
            expression.symbol.actualizeIfNeeded(),
            expression.typeArgumentsCount,
            expression.valueArgumentsCount,
            expression.reflectionTarget,
            expression.origin
        ).also {
            it.extensionReceiver = expression.extensionReceiver
            it.dispatchReceiver = expression.dispatchReceiver
            for (index in 0 until expression.valueArgumentsCount) {
                it.putValueArgument(index, expression.getValueArgument(index))
            }
            for (index in 0 until expression.typeArgumentsCount) {
                it.putTypeArgument(index, expression.getTypeArgument(index))
            }
        }
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val result = visitFunctionAccess(expression) as IrCall
        val actualSymbol = expectActualMap[result.symbol] as? IrSimpleFunctionSymbol
        return actualSymbol?.let {
            IrCallImpl(
                result.startOffset,
                result.endOffset,
                result.type,
                actualSymbol,
                result.typeArgumentsCount,
                result.valueArgumentsCount,
                result.origin,
                result.superQualifierSymbol
            ).also {
                it.extensionReceiver = result.extensionReceiver
                it.dispatchReceiver = result.dispatchReceiver
                for (index in 0 until result.valueArgumentsCount) {
                    it.putValueArgument(index, result.getValueArgument(index))
                }
                for (index in 0 until result.typeArgumentsCount) {
                    it.putTypeArgument(index, result.getTypeArgument(index))
                }
            }
        } ?: result
    }

    private fun IrFunctionSymbol.actualizeIfNeeded(): IrFunctionSymbol {
        return (expectActualMap[this] as? IrFunctionSymbol) ?: this
    }

    private fun IrType.actualizeIfNeeded(): IrType {
        if (this !is IrSimpleType) return this
        val newArguments = if (arguments.isNotEmpty()) {
            val resultArguments = ArrayList<IrTypeArgument>(arguments.size)
            for (argument in arguments) {
                resultArguments.add((argument.typeOrNull?.actualizeIfNeeded() as? IrTypeArgument) ?: argument)
            }
            resultArguments
        } else {
            arguments
        }

        val newType = (expectActualMap[classifier] as? IrClassifierSymbol) ?: type.classifierOrFail

        return IrSimpleTypeImpl(
            newType,
            nullability,
            newArguments,
            annotations.map { visitConstructorCall(it) as IrConstructorCall },
            abbreviation
        )
    }
}