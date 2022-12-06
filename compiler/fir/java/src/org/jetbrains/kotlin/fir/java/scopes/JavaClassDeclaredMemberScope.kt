package org.jetbrains.kotlin.fir.java.scopes

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.java.JavaTypeParameterStack
import org.jetbrains.kotlin.fir.scopes.FirContainingNamesAwareScope
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.isStatic
import org.jetbrains.kotlin.name.Name

class JavaClassDeclaredMemberScope internal constructor(
    session: FirSession,
    private val declaredMemberScope: FirContainingNamesAwareScope,
    javaTypeParameterStack: JavaTypeParameterStack,
): FirContainingNamesAwareScope() {
    private val functions = hashMapOf<Name, Collection<FirNamedFunctionSymbol>>()
    private val properties = hashMapOf<Name, Collection<FirVariableSymbol<*>>>()
    private val overrideChecker = JavaOverrideChecker(session, javaTypeParameterStack, baseScopes = null, considerReturnTypeKinds = false)

    override fun processFunctionsByName(name: Name, processor: (FirNamedFunctionSymbol) -> Unit) {
        functions.getOrPut(name) {
            computeFunctions(name)
        }.forEach(processor)
    }

    private fun computeFunctions(name: Name): MutableList<FirNamedFunctionSymbol> {
        val result = mutableListOf<FirNamedFunctionSymbol>()
        declaredMemberScope.processFunctionsByName(name) l@{ functionSymbol ->
            if (!functionSymbol.isStatic) return@l
            result.add(functionSymbol)
        }

        return result
    }

    override fun processPropertiesByName(name: Name, processor: (FirVariableSymbol<*>) -> Unit) {
        return properties.getOrPut(name) {
            computeProperties(name)
        }.forEach(processor)

    }

    private fun computeProperties(name: Name): MutableList<FirVariableSymbol<*>> {
        val result: MutableList<FirVariableSymbol<*>> = mutableListOf()
        declaredMemberScope.processPropertiesByName(name) l@{ propertySymbol ->
            if (!propertySymbol.isStatic) return@l
            result.add(propertySymbol)
        }

        if (result.isNotEmpty()) return result
        return result
    }

    override fun getCallableNames(): Set<Name> {
        return buildSet {
            addAll(declaredMemberScope.getCallableNames())
        }
    }

    override fun getClassifierNames(): Set<Name> {
        return buildSet {
            addAll(declaredMemberScope.getClassifierNames())
        }
    }

    override fun mayContainName(name: Name): Boolean {
        return declaredMemberScope.mayContainName(name)
    }

    override val scopeOwnerLookupNames: List<String>
        get() = declaredMemberScope.scopeOwnerLookupNames
}