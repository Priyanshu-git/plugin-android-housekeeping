package com.example.housekeeping.adapters.uast

import com.example.housekeeping.core.spi.SyntheticFilter
import com.intellij.psi.PsiFile
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod

class UastSyntheticFilter : SyntheticFilter {

    companion object {
        private val SYNTHETIC_METHOD_NAMES = setOf(
            "values", "valueOf", "entries",
            "hashCode", "equals", "toString", "copy",
            "<init>", "<clinit>"
        )
    }

    override fun isSyntheticMethod(method: UMethod): Boolean {
        return method.sourcePsi == null
            || method.name in SYNTHETIC_METHOD_NAMES
            || method.name.startsWith("component")
            || method.name.startsWith("copy\$default")
    }

    override fun isSyntheticClass(uClass: UClass): Boolean {
        return uClass.sourcePsi == null || uClass.sourcePsi is PsiFile
    }
}
