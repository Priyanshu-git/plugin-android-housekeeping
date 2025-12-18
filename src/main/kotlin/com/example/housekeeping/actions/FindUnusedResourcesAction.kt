package com.example.housekeeping.actions

import com.example.housekeeping.analysis.ResourceAnalyzer
import com.example.housekeeping.ui.HousekeepingPanel
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class FindUnusedResourcesAction : BaseHousekeepingAction() {

    override fun isApplicable(e: AnActionEvent): Boolean {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return false
        return file.path.contains("/res")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = project(e)
        val analyzer = ResourceAnalyzer(project)
        val results = analyzer.analyze(e)

        HousekeepingPanel.show(project, results)
    }
}
