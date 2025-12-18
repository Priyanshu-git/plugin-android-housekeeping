package com.example.housekeeping.analysis

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod

sealed class AnalysisResult {

    data class MethodResult(
        val method: PsiMethod,
        val reason: String
    ) : AnalysisResult()

    data class ClassResult(
        val clazz: PsiClass,
        val reason: String
    ) : AnalysisResult()

    data class ResourceResult(
        val file: VirtualFile,
        val reason: String
    ) : AnalysisResult()
}
