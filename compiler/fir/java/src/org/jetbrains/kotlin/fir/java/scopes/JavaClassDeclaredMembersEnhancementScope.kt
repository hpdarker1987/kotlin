package org.jetbrains.kotlin.fir.java.scopes

import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.scopes.FirContainingNamesAwareScope
import org.jetbrains.kotlin.fir.scopes.FirTypeScope
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.name.Name

class JavaClassDeclaredMembersEnhancementScope(
    private val owner: FirRegularClassSymbol,
    private val useSiteDeclaredMemberScope: FirContainingNamesAwareScope,
    private val useSiteMembersEnhancementScope: JavaClassMembersEnhancementScope
): FirTypeScope() {
    override fun processDirectOverriddenFunctionsWithBaseScope(
        functionSymbol: FirNamedFunctionSymbol,
        processor: (FirNamedFunctionSymbol, FirTypeScope) -> ProcessorAction
    ): ProcessorAction {
        return useSiteMembersEnhancementScope.processDirectOverriddenFunctionsWithBaseScope(functionSymbol, processor)
    }

    override fun processDirectOverriddenPropertiesWithBaseScope(
        propertySymbol: FirPropertySymbol,
        processor: (FirPropertySymbol, FirTypeScope) -> ProcessorAction
    ): ProcessorAction {
        return useSiteMembersEnhancementScope.processDirectOverriddenPropertiesWithBaseScope(propertySymbol, processor)
    }

    override fun getCallableNames(): Set<Name> {
        return useSiteDeclaredMemberScope.getCallableNames()
    }

    override fun getClassifierNames(): Set<Name> {
        return useSiteDeclaredMemberScope.getClassifierNames()
    }

    override fun processFunctionsByName(name: Name, processor: (FirNamedFunctionSymbol) -> Unit) {
        useSiteMembersEnhancementScope.processFunctionsByName(name, processor)
    }

    override fun processClassifiersByNameWithSubstitution(
        name: Name,
        processor: (FirClassifierSymbol<*>, ConeSubstitutor) -> Unit
    ) {
        useSiteMembersEnhancementScope.processClassifiersByNameWithSubstitution(name, processor)
    }

    override fun processDeclaredConstructors(processor: (FirConstructorSymbol) -> Unit) {
        useSiteMembersEnhancementScope.processDeclaredConstructors(processor)
    }

    override fun processPropertiesByName(name: Name, processor: (FirVariableSymbol<*>) -> Unit) {
        useSiteMembersEnhancementScope.processPropertiesByName(name, processor)
    }

    override fun toString(): String {
        return "Java enhancement declared member scope for ${owner.classId}"
    }
}