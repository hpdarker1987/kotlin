/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.plugin

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.containingClassForStaticMemberAttr
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.builder.*
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.declarations.utils.isInner
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildDelegatedConstructorCall
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.getContainingClassLookupTag
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.scope
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.getDeclaredConstructors
import org.jetbrains.kotlin.fir.scopes.impl.declaredMemberScope
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol
import org.jetbrains.kotlin.name.callableIdForConstructor

class ConstructorBuildingContext(
    session: FirSession,
    key: GeneratedDeclarationKey,
    owner: FirClassSymbol<*>,
    private val isPrimary: Boolean,
    private val contextReceiverTypes: List<ConeKotlinType>
) : FunctionBuildingContext<FirConstructor>(owner.classId.callableIdForConstructor(), session, key, owner) {
    override fun build(): FirConstructor {
        requireNotNull(owner)
        val init: FirAbstractConstructorBuilder.() -> Unit = {
            symbol = FirConstructorSymbol(owner.classId)

            resolvePhase = FirResolvePhase.BODY_RESOLVE
            moduleData = session.moduleData
            origin = key.origin

            owner.typeParameterSymbols.mapTo(typeParameters) { buildConstructedClassTypeParameterRef { symbol = it }}
            returnTypeRef = owner.defaultType().toFirResolvedTypeRef()
            status = generateStatus()
            if (owner.isInner) {
                val parentSymbol = owner.getContainingClassLookupTag()?.toSymbol(session) as? FirClassSymbol<*>
                    ?: error("Symbol for parent of $owner not found")
                dispatchReceiverType = parentSymbol.defaultType()
            }
            this@ConstructorBuildingContext.valueParameters.mapTo(valueParameters) { generateValueParameter(it, symbol) }
            contextReceiverTypes.mapTo(contextReceivers) { buildContextReceiver { typeRef = it.toFirResolvedTypeRef() }}
        }
        val constructor = if (isPrimary) {
            buildPrimaryConstructor(init)
        } else {
            buildConstructor(init)
        }
        constructor.containingClassForStaticMemberAttr = owner.toLookupTag()
        return constructor
    }
}

// ---------------------------------------------------------------------------------------------------------------------

context(FirDeclarationGenerationExtension)
fun createConstructor(
    owner: FirClassSymbol<*>,
    key: GeneratedDeclarationKey,
    isPrimary: Boolean = false,
    contextReceiverTypes: List<ConeKotlinType> = emptyList(),
    config: ConstructorBuildingContext.() -> Unit = {}
): FirConstructor {
    return ConstructorBuildingContext(session, key, owner, isPrimary, contextReceiverTypes).apply(config).build()
}

context(FirDeclarationGenerationExtension)
fun createDefaultConstructorForObject(
    owner: FirClassSymbol<*>,
    key: GeneratedDeclarationKey,
    contextReceiverTypes: List<ConeKotlinType> = emptyList()
): FirConstructor {
    require(owner.classKind == ClassKind.OBJECT)
    return ConstructorBuildingContext(session, key, owner, isPrimary = true, contextReceiverTypes).apply {
        visibility = Visibilities.Private
    }.build().apply {
        val delegatingConstructorCall = buildDelegatedConstructorCall {
            val supertypes = owner.resolvedSuperTypes.filter { it.toRegularClassSymbol(session)?.classKind == ClassKind.CLASS }
            val singleSupertype = when (supertypes.size) {
                0 -> session.builtinTypes.anyType.type
                1 -> supertypes.first()
                else -> error("Object $owner has more than one class supertypes: $supertypes")
            }
            constructedTypeRef = singleSupertype.toFirResolvedTypeRef()
            val superSymbol = singleSupertype.toRegularClassSymbol(session) ?: error("Symbol for supertype $singleSupertype not found")
            val superConstructorSymbol = superSymbol.declaredMemberScope(session)
                .getDeclaredConstructors()
                .firstOrNull { it.valueParameterSymbols.isEmpty() }
                ?: error("No arguments constructor for class $singleSupertype not found")
            calleeReference = buildResolvedNamedReference {
                name = superConstructorSymbol.name
                resolvedSymbol = superConstructorSymbol
            }
            argumentList = buildResolvedArgumentList(LinkedHashMap())
            isThis = false
        }
        replaceDelegatedConstructor(delegatingConstructorCall)
    }
}
