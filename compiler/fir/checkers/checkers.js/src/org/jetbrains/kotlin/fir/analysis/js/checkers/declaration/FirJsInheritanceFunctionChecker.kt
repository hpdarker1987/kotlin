/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.js.checkers.declaration

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.isEffectivelyExternal
import org.jetbrains.kotlin.fir.analysis.checkers.isOverridingExternalWithOptionalParams
import org.jetbrains.kotlin.fir.analysis.diagnostics.js.FirJsErrors
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction

object FirJsInheritanceFunctionChecker : FirFunctionChecker() {
    override fun check(declaration: FirFunction, context: CheckerContext, reporter: DiagnosticReporter) {
        if (declaration.isNotEffectivelyExternalFunctionButOverridesExternal(context)) {
            reporter.reportOn(declaration.source, FirJsErrors.OVERRIDING_EXTERNAL_FUN_WITH_OPTIONAL_PARAMS, context)
        }
    }

    private fun FirDeclaration.isNotEffectivelyExternalFunctionButOverridesExternal(context: CheckerContext): Boolean {
        if (this !is FirFunction || symbol.isEffectivelyExternal(context)) return false
        return symbol.isOverridingExternalWithOptionalParams(context)
    }
}
