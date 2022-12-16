/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.light.classes.symbol.annotations

import com.intellij.psi.CommonClassNames.JAVA_LANG_ANNOTATION_RETENTION
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.light.LightReferenceListBuilder
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.annotations.*
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.symbols.KtFileSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.types.KtTypeMappingMode
import org.jetbrains.kotlin.builtins.StandardNames.DEFAULT_VALUE_PARAMETER
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.light.classes.symbol.NullabilityType
import org.jetbrains.kotlin.light.classes.symbol.classes.SymbolLightClassBase
import org.jetbrains.kotlin.light.classes.symbol.methods.SymbolLightAccessorMethod
import org.jetbrains.kotlin.light.classes.symbol.methods.SymbolLightMethod
import org.jetbrains.kotlin.load.java.JvmAbi.JVM_FIELD_ANNOTATION_CLASS_ID
import org.jetbrains.kotlin.load.java.JvmAnnotationNames.RETENTION_POLICY_ENUM
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.name.JvmNames.JVM_MULTIFILE_CLASS_ID
import org.jetbrains.kotlin.name.JvmNames.JVM_NAME_CLASS_ID
import org.jetbrains.kotlin.name.JvmNames.JVM_OVERLOADS_CLASS_ID
import org.jetbrains.kotlin.name.JvmNames.JVM_SYNTHETIC_ANNOTATION_CLASS_ID
import org.jetbrains.kotlin.resolve.annotations.JVM_STATIC_ANNOTATION_CLASS_ID
import org.jetbrains.kotlin.resolve.annotations.JVM_THROWS_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.resolve.deprecation.DeprecationLevelValue
import org.jetbrains.kotlin.resolve.inline.INLINE_ONLY_ANNOTATION_FQ_NAME

internal fun KtAnnotatedSymbol.hasJvmSyntheticAnnotation(
    annotationUseSiteTarget: AnnotationUseSiteTarget? = null,
    strictUseSite: Boolean = true,
): Boolean = hasAnnotation(JVM_SYNTHETIC_ANNOTATION_CLASS_ID, annotationUseSiteTarget, strictUseSite)

internal fun KtFileSymbol.hasJvmMultifileClassAnnotation(): Boolean =
    hasAnnotation(JVM_MULTIFILE_CLASS_ID, AnnotationUseSiteTarget.FILE)

internal fun KtAnnotatedSymbol.getJvmNameFromAnnotation(annotationUseSiteTarget: AnnotationUseSiteTarget? = null): String? {
    val annotation = annotations.firstOrNull {
        val siteTarget = it.useSiteTarget
        (siteTarget == null || siteTarget == annotationUseSiteTarget) &&
                it.classId == JVM_NAME_CLASS_ID
    }

    return annotation?.let {
        (it.arguments.firstOrNull()?.expression as? KtConstantAnnotationValue)?.constantValue?.value as? String
    }
}

context(KtAnalysisSession)
internal fun isHiddenByDeprecation(
    symbol: KtAnnotatedSymbol,
    annotationUseSiteTarget: AnnotationUseSiteTarget? = null,
): Boolean {
    return symbol.getDeprecationStatus(annotationUseSiteTarget)?.deprecationLevel == DeprecationLevelValue.HIDDEN
}

context(KtAnalysisSession)
internal fun KtAnnotatedSymbol.isHiddenOrSynthetic(
    annotationUseSiteTarget: AnnotationUseSiteTarget? = null,
    strictUseSite: Boolean = true,
) = isHiddenByDeprecation(this, annotationUseSiteTarget) || hasJvmSyntheticAnnotation(annotationUseSiteTarget, strictUseSite)

internal fun KtAnnotatedSymbol.hasJvmFieldAnnotation(): Boolean =
    hasAnnotation(JVM_FIELD_ANNOTATION_CLASS_ID, null)

internal fun KtAnnotatedSymbol.hasPublishedApiAnnotation(annotationUseSiteTarget: AnnotationUseSiteTarget? = null): Boolean =
    hasAnnotation(StandardClassIds.Annotations.PublishedApi, annotationUseSiteTarget)

internal fun KtAnnotatedSymbol.hasDeprecatedAnnotation(
    annotationUseSiteTarget: AnnotationUseSiteTarget? = null,
    strictUseSite: Boolean = true,
): Boolean = hasAnnotation(StandardClassIds.Annotations.Deprecated, annotationUseSiteTarget, strictUseSite)

internal fun KtAnnotatedSymbol.hasJvmOverloadsAnnotation(): Boolean = hasAnnotation(JVM_OVERLOADS_CLASS_ID, null)

internal fun KtAnnotatedSymbol.hasJvmStaticAnnotation(
    annotationUseSiteTarget: AnnotationUseSiteTarget? = null,
    strictUseSite: Boolean = true,
): Boolean = hasAnnotation(JVM_STATIC_ANNOTATION_CLASS_ID, annotationUseSiteTarget, strictUseSite)

internal fun KtAnnotatedSymbol.hasInlineOnlyAnnotation(): Boolean =
    hasAnnotation(INLINE_ONLY_ANNOTATION_FQ_NAME, null)

internal fun KtAnnotatedSymbol.hasAnnotation(
    classId: ClassId,
    annotationUseSiteTarget: AnnotationUseSiteTarget?,
    strictUseSite: Boolean = true,
): Boolean = findAnnotation(classId, annotationUseSiteTarget, strictUseSite) != null

internal fun KtAnnotatedSymbol.findAnnotation(
    classId: ClassId,
    annotationUseSiteTarget: AnnotationUseSiteTarget?,
    strictUseSite: Boolean = true,
): KtAnnotationApplication? =
    annotations.find {
        val useSiteTarget = it.useSiteTarget
        (useSiteTarget == annotationUseSiteTarget || !strictUseSite && useSiteTarget == null) && it.classId == classId
    }

internal fun KtAnnotatedSymbol.hasAnnotation(
    fqName: FqName,
    annotationUseSiteTarget: AnnotationUseSiteTarget?,
    strictUseSite: Boolean = true,
): Boolean = findAnnotation(fqName, annotationUseSiteTarget, strictUseSite) != null

internal fun KtAnnotatedSymbol.findAnnotation(
    fqName: FqName,
    annotationUseSiteTarget: AnnotationUseSiteTarget?,
    strictUseSite: Boolean = true,
): KtAnnotationApplication? =
    annotations.find {
        val useSiteTarget = it.useSiteTarget
        (useSiteTarget == annotationUseSiteTarget || !strictUseSite && useSiteTarget == null) && it.classId?.asSingleFqName() == fqName
    }

internal fun NullabilityType.computeNullabilityAnnotation(parent: PsiModifierList): SymbolLightSimpleAnnotation? {
    return when (this) {
        NullabilityType.NotNull -> NotNull::class.java
        NullabilityType.Nullable -> Nullable::class.java
        else -> null
    }?.let {
        SymbolLightSimpleAnnotation(it.name, parent)
    }
}

internal fun KtAnnotatedSymbol.computeAnnotations(
    modifierList: PsiModifierList,
    nullability: NullabilityType,
    annotationUseSiteTarget: AnnotationUseSiteTarget?,
    includeAnnotationsWithoutSite: Boolean = true,
    doNotAddOverrideAnnotation: Boolean = false
): List<PsiAnnotation> {
    val parent = modifierList.parent
    val nullabilityAnnotation = nullability.computeNullabilityAnnotation(modifierList)
    val parentIsAnnotation = (parent as? PsiClass)?.isAnnotationType == true

    val result = mutableListOf<PsiAnnotation>()

    if (!doNotAddOverrideAnnotation) {
        if (parent is SymbolLightMethod<*> && (parent.isDelegated || parent.isOverride())) {
            result.add(SymbolLightSimpleAnnotation(java.lang.Override::class.java.name, modifierList))
        }
    }

    if (annotations.isEmpty()) {
        if (parentIsAnnotation) {
            result.add(createRetentionRuntimeAnnotation(modifierList))
        }

        if (nullabilityAnnotation != null) {
            result.add(nullabilityAnnotation)
        }

        return result
    }

    for (annotation in annotations) {

        val siteTarget = annotation.useSiteTarget

        if ((includeAnnotationsWithoutSite && siteTarget == null) ||
            siteTarget == annotationUseSiteTarget
        ) {
            result.add(SymbolLightAnnotationForAnnotationCall(annotation, modifierList))
        }
    }

    if (parentIsAnnotation &&
        annotations.none { it.classId?.asFqNameString() == JAVA_LANG_ANNOTATION_RETENTION }
    ) {
        val argumentWithKotlinRetention = annotations.firstOrNull { it.classId == StandardClassIds.Annotations.Retention }
            ?.arguments
            ?.firstOrNull { it.name.asString() == "value" }
            ?.expression
        val kotlinRetentionName = (argumentWithKotlinRetention as? KtEnumEntryAnnotationValue)?.callableId?.callableName?.asString()
        result.add(createRetentionRuntimeAnnotation(modifierList, kotlinRetentionName))
    }

    if (nullabilityAnnotation != null) {
        result.add(nullabilityAnnotation)
    }

    return result
}

private fun createRetentionRuntimeAnnotation(modifierList: PsiModifierList, retentionName: String? = null): PsiAnnotation =
    SymbolLightSimpleAnnotation(
        JAVA_LANG_ANNOTATION_RETENTION,
        modifierList,
        listOf(
            KtNamedAnnotationValue(
                name = DEFAULT_VALUE_PARAMETER,
                expression = KtEnumEntryAnnotationValue(
                    callableId = CallableId(
                        ClassId.fromString(RETENTION_POLICY_ENUM.asString()),
                        Name.identifier(retentionName ?: AnnotationRetention.RUNTIME.name),
                    ),
                    sourcePsi = null,
                )
            )
        )
    )

context(KtAnalysisSession)
internal fun KtAnnotatedSymbol.computeThrowsList(
    builder: LightReferenceListBuilder,
    annotationUseSiteTarget: AnnotationUseSiteTarget?,
    useSitePosition: PsiElement,
    containingClass: SymbolLightClassBase,
    strictUseSite: Boolean = true,
) {
    val annoApp = findAnnotation(JVM_THROWS_ANNOTATION_FQ_NAME, annotationUseSiteTarget, strictUseSite) ?: return

    fun handleAnnotationValue(annotationValue: KtAnnotationValue) = when (annotationValue) {
        is KtArrayAnnotationValue -> {
            annotationValue.values.forEach(::handleAnnotationValue)
        }
        is KtKClassAnnotationValue.KtNonLocalKClassAnnotationValue -> {
            val psiType = buildClassType(annotationValue.classId).asPsiType(
                useSitePosition,
                KtTypeMappingMode.DEFAULT,
                containingClass.isAnnotationType
            )
            (psiType as? PsiClassType)?.let {
                builder.addReference(it)
            }
        }
        else -> {}
    }

    annoApp.arguments.forEach { handleAnnotationValue(it.expression) }
}
