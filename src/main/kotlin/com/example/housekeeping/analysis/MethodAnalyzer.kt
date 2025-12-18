package com.example.housekeeping.analysis

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil

class MethodAnalyzer(private val project: Project) {

    fun analyze(e: AnActionEvent): List<AnalysisResult> {
        val scope = GlobalSearchScope.projectScope(project)

        val methods = PsiTreeUtil.collectElementsOfType(
            e.getData(CommonDataKeys.PSI_FILE),
            PsiMethod::class.java
        )

        return methods
            .filter { isUnused(it, scope) }
            .map {
                AnalysisResult.MethodResult(
                    method = it,
                    reason = "No references found in project scope"
                )
            }
    }

    private fun isUnused(method: PsiMethod, scope: SearchScope): Boolean {
        if (!method.hasModifierProperty(PsiModifier.PRIVATE)) return false
        if (method.annotations.isNotEmpty()) return false

        return ReferencesSearch.search(method, scope).findFirst() == null
    }
}
