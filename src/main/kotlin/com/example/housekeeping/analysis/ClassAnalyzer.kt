package com.example.housekeeping.analysis

import com.example.housekeeping.util.AndroidUtil
import com.example.housekeeping.util.PsiUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.searches.ReferencesSearch

class ClassAnalyzer(private val project: Project) {

    fun analyze(e: AnActionEvent): List<AnalysisResult> {
        val classes = PsiUtil.collectClasses(e) ?: return emptyList()

        return classes
            .filter { isUnused(it) }
            .map {
                AnalysisResult.ClassResult(
                    clazz = it,
                    reason = "No references found and not an Android entry point"
                )
            }
    }

    private fun isUnused(psiClass: PsiClass): Boolean {
        if (!psiClass.isValid) return false
        if (psiClass.isInterface || psiClass.isEnum) return false

        if (AndroidUtil.isAndroidEntryPoint(psiClass)) return false
        if (psiClass.annotations.isNotEmpty()) return false

        val refs = ReferencesSearch.search(psiClass).findFirst()
        return refs == null
    }
}
