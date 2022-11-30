/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.js.checkers.expression

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirAnnotationCallChecker
import org.jetbrains.kotlin.fir.analysis.diagnostics.js.FirJsErrors
import org.jetbrains.kotlin.fir.declarations.fullyExpandedClassId
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirConstExpression
import org.jetbrains.kotlin.name.JsStandardClassIds.Annotations.JsQualifier

object FirJsQualifierChecker : FirAnnotationCallChecker() {
    override fun check(expression: FirAnnotationCall, context: CheckerContext, reporter: DiagnosticReporter) {
        val annotationFqName = expression.fullyExpandedClassId(context.session)?.asSingleFqName()

        if (annotationFqName != JsQualifier.asSingleFqName()) {
            return
        }

        val string = (expression.argumentMapping.mapping.values.firstOrNull() as? FirConstExpression<*>)?.value as? String ?: return

        if (!validateQualifier(string)) {
            reporter.reportOn(expression.argumentList.arguments.first().source, FirJsErrors.WRONG_JS_QUALIFIER, context)
        }
    }

    private fun validateQualifier(qualifier: String): Boolean {
        val parts = qualifier.split('.')
        if (parts.isEmpty()) return false

        return parts.all { part ->
            part.isNotEmpty() && part[0].isJavaIdentifierStart() && part.drop(1).all(Char::isJavaIdentifierPart)
        }
    }
}
