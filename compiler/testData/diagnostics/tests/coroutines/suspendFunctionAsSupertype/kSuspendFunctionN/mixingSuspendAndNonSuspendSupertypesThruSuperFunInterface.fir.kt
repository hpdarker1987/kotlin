// FIR_DISABLE_LAZY_RESOLVE_CHECKS
// !LANGUAGE: +SuspendFunctionAsSupertype
// SKIP_TXT
// DIAGNOSTICS: -CONFLICTING_INHERITED_MEMBERS, -CONFLICTING_OVERLOADS, -ABSTRACT_MEMBER_NOT_IMPLEMENTED, -ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED

import kotlin.reflect.*

fun interface FISuper: () -> Unit

class C: KSuspendFunction0<Unit>, FISuper {
    override suspend fun invoke() {
    }
}

interface I: KSuspendFunction0<Unit>, FISuper {
}

object O: KSuspendFunction0<Unit>, FISuper {
    override suspend fun invoke() {
    }
}