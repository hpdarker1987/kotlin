/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.unlinked

import org.jetbrains.kotlin.builtins.FunctionInterfacePackageFragment
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.ir.UNDEFINED_COLUMN_NUMBER
import org.jetbrains.kotlin.ir.UNDEFINED_LINE_NUMBER
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.util.IrMessageLogger.Location
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name

internal object PartialLinkageUtils {
    val UNKNOWN_NAME = Name.identifier("<unknown name>")

    fun IdSignature.guessName(nameSegmentsToPickUp: Int): String? = when (this) {
        is IdSignature.CommonSignature -> if (nameSegmentsToPickUp == 1)
            shortName
        else
            nameSegments.takeLast(nameSegmentsToPickUp).joinToString(".")

        is IdSignature.CompositeSignature -> inner.guessName(nameSegmentsToPickUp)
        is IdSignature.AccessorSignature -> accessorSignature.guessName(nameSegmentsToPickUp)

        else -> null
    }

    /** For fast check if a declaration is in the module */
    sealed interface Module {
        val name: String

        data class Real(override val name: String) : Module {
            constructor(name: Name) : this(name.asString())
        }

        object SyntheticBuiltInFunctions : Module {
            override val name = "<synthetic built-in functions>"
        }

        object MissingDeclarations : Module {
            override val name = "<missing declarations>"
        }

        fun defaultLocationWithoutPath() = Location(name, UNDEFINED_LINE_NUMBER, UNDEFINED_COLUMN_NUMBER)

        companion object {
            fun determineModuleFor(declaration: IrDeclaration): Module = determineFor(
                declaration,
                onMissingDeclaration = MissingDeclarations,
                onSyntheticBuiltInFunction = SyntheticBuiltInFunctions,
                onIrBased = { Real(it.module.name) },
                onLazyIrBased = { Real(it.containingDeclaration.name) },
                onError = { error("Can't determine module for $declaration, name=${(declaration as? IrDeclarationWithName)?.name}") }
            )
        }
    }

    sealed interface File {
        val module: Module
        fun computeLocationForOffset(offset: Int): Location

        data class IrBased(private val file: IrFile) : File {
            override val module = Module.Real(file.module.name)

            override fun computeLocationForOffset(offset: Int): Location {
                val lineNumber = if (offset == UNDEFINED_OFFSET) UNDEFINED_LINE_NUMBER else file.fileEntry.getLineNumber(offset)
                val columnNumber = if (offset == UNDEFINED_OFFSET) UNDEFINED_COLUMN_NUMBER else file.fileEntry.getColumnNumber(offset)

                // TODO: should module name still be added here?
                return Location("${module.name} @ ${file.fileEntry.name}", lineNumber, columnNumber)
            }
        }

        class LazyIrBased(packageFragmentDescriptor: PackageFragmentDescriptor) : File {
            override val module = Module.Real(packageFragmentDescriptor.containingDeclaration.name)
            private val defaultLocation = module.defaultLocationWithoutPath()

            override fun equals(other: Any?) = (other as? LazyIrBased)?.module == module
            override fun hashCode() = module.hashCode()

            override fun computeLocationForOffset(offset: Int) = defaultLocation
        }

        object SyntheticBuiltInFunctions : File {
            override val module = Module.SyntheticBuiltInFunctions
            private val defaultLocation = module.defaultLocationWithoutPath()

            override fun computeLocationForOffset(offset: Int) = defaultLocation
        }

        object MissingDeclarations : File {
            override val module = Module.MissingDeclarations
            private val defaultLocation = module.defaultLocationWithoutPath()

            override fun computeLocationForOffset(offset: Int) = defaultLocation
        }

        companion object {
            fun determineFileFor(declaration: IrDeclaration): File = determineFor(
                declaration,
                onMissingDeclaration = MissingDeclarations,
                onSyntheticBuiltInFunction = SyntheticBuiltInFunctions,
                onIrBased = ::IrBased,
                onLazyIrBased = ::LazyIrBased,
                onError = { error("Can't determine file for $declaration, name=${(declaration as? IrDeclarationWithName)?.name}") }
            )
        }
    }

    private inline fun <R> determineFor(
        declaration: IrDeclaration,
        onMissingDeclaration: R,
        onSyntheticBuiltInFunction: R,
        onIrBased: (IrFile) -> R,
        onLazyIrBased: (PackageFragmentDescriptor) -> R,
        onError: () -> Nothing
    ): R {
        return if (declaration.origin == PartiallyLinkedDeclarationOrigin.MISSING_DECLARATION)
            onMissingDeclaration
        else {
            val packageFragment = declaration.getPackageFragment()
            val packageFragmentDescriptor = with(packageFragment.symbol) { if (hasDescriptor) descriptor else null }

            when {
                packageFragmentDescriptor is FunctionInterfacePackageFragment -> onSyntheticBuiltInFunction
                packageFragment is IrFile -> onIrBased(packageFragment)
                packageFragment is IrExternalPackageFragment && packageFragmentDescriptor != null -> onLazyIrBased(packageFragmentDescriptor)
                else -> onError()
            }
        }
    }
}

/** An optimization to avoid re-computing file for every visited declaration */
internal abstract class FileAwareIrElementTransformerVoid(startingFile: PartialLinkageUtils.File?) : IrElementTransformerVoid() {
    private var _currentFile: PartialLinkageUtils.File? = startingFile
    protected val currentFile: PartialLinkageUtils.File get() = _currentFile ?: error("No information about current file")

    override fun visitFile(declaration: IrFile): IrFile {
        _currentFile = PartialLinkageUtils.File.IrBased(declaration)
        return try {
            super.visitFile(declaration)
        } finally {
            _currentFile = null
        }
    }
}
