package com.housekeeping.actions

import com.example.housekeeping.actions.BaseHousekeepingAction
import com.example.housekeeping.analysis.ClassAnalyzer
import com.example.housekeeping.ui.HousekeepingPanel
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiPackage

class FindUnusedClassesAction : BaseHousekeepingAction() {

    override fun isApplicable(e: AnActionEvent): Boolean {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        val file = e.getData(CommonDataKeys.PSI_FILE)

        return element is PsiClass ||
                element is PsiPackage ||
                file is PsiJavaFile ||
                file is PsiFile
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = project(e)
        val analyzer = ClassAnalyzer(project)
        val results = analyzer.analyze(e)

        HousekeepingPanel.show(project, results)
    }
}
