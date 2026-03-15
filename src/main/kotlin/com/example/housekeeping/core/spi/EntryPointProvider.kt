package com.example.housekeeping.core.spi

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass

interface EntryPointProvider {

    fun isApplicable(project: Project): Boolean

    fun isEntryPoint(psiClass: PsiClass): Boolean

    companion object {
        @JvmField
        val EP_NAME = ExtensionPointName<EntryPointProvider>("com.nexxlabs.housekeeping.entryPointProvider")
    }
}
