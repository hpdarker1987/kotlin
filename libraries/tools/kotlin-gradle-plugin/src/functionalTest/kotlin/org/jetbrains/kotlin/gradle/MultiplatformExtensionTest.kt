/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

/* Associate compilations are not yet supported by the IDE. KT-34102 */
@file:Suppress("invisible_reference", "invisible_member", "FunctionName", "DuplicatedCode")

package org.jetbrains.kotlin.gradle

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinSharedNativeCompilation
import org.jetbrains.kotlin.gradle.targets.native.internal.CInteropCommonizerDependent
import org.jetbrains.kotlin.gradle.targets.native.internal.CInteropIdentifier
import org.jetbrains.kotlin.gradle.targets.native.internal.from
import org.jetbrains.kotlin.gradle.util.*
import kotlin.test.BeforeTest
import kotlin.test.assertNotNull


abstract class MultiplatformExtensionTest {
    protected val project: ProjectInternal = ProjectBuilder.builder().build() as ProjectInternal
    protected lateinit var kotlin: KotlinMultiplatformExtension

    @BeforeTest
    open fun setup() {
        kotlin = project.applyMultiplatformPlugin()
    }

    protected fun enableGranularSourceSetsMetadata() {
        project.enableGranularSourceSetsMetadata()
    }

    protected fun enableCInteropCommonization() {
        project.enableCInteropCommonization()
    }

    internal fun expectCInteropCommonizerDependent(compilation: KotlinSharedNativeCompilation): CInteropCommonizerDependent {
        return assertNotNull(
            CInteropCommonizerDependent.from(compilation), "Can't find SharedInterops for ${compilation.name} compilation"
        )
    }

    internal fun expectCInteropCommonizerDependent(sourceSet: KotlinSourceSet): CInteropCommonizerDependent {
        return assertNotNull(
            CInteropCommonizerDependent.from(sourceSet), "Can't find SharedInterops for ${sourceSet.name} source set"
        )
    }

    internal fun findCInteropCommonizerDependent(compilation: KotlinSharedNativeCompilation): CInteropCommonizerDependent? {
        return CInteropCommonizerDependent.from(compilation)
    }

    internal fun findCInteropCommonizerDependent(sourceSet: KotlinSourceSet): CInteropCommonizerDependent? {
        return CInteropCommonizerDependent.from(sourceSet)
    }

    internal fun expectSharedNativeCompilation(sourceSet: KotlinSourceSet): KotlinSharedNativeCompilation {
        return kotlin.targets.flatMap { it.compilations }.filterIsInstance<KotlinSharedNativeCompilation>()
            .single { it.defaultSourceSet == sourceSet }
    }

    internal fun KotlinNativeTarget.mainCinteropIdentifier(name: String): CInteropIdentifier {
        return compilations.getByName("main").cinteropIdentifier(name)
    }

    internal fun KotlinNativeTarget.testCinteropIdentifier(name: String): CInteropIdentifier {
        return compilations.getByName("test").cinteropIdentifier(name)
    }

    internal fun KotlinCompilation<*>.cinteropIdentifier(name: String): CInteropIdentifier {
        return CInteropIdentifier(CInteropIdentifier.Scope.create(this), name)
    }
}
