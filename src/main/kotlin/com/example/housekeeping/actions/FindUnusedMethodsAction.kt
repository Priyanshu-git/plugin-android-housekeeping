package com.example.housekeeping.actions

import com.example.housekeeping.analysis.MethodAnalyzer
import com.example.housekeeping.ui.HousekeepingPanel
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiMethod

class FindUnusedMethodsAction : BaseHousekeepingAction() {

    override fun isApplicable(e: AnActionEvent): Boolean {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        return element is PsiMethod || e.getData(CommonDataKeys.PSI_FILE) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = project(e)
        val analyzer = MethodAnalyzer(project)
        val results = analyzer.analyze(e)

        HousekeepingPanel.show(project, results)
    }
}
