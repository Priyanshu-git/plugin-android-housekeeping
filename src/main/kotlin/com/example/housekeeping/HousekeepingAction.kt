package com.example.housekeeping

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager

// --- Base Action ---
abstract class BaseHousekeepingAction(private val mode: AnalysisMode) : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 1. Get Context Data
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val scopeElements = mutableListOf<PsiElement>()

        // 2. Determine Scope (Simplified: If it looks like a file/dir, add it)
        if (psiElement != null && psiElement !is PsiDirectory) {
            // It's a code element (Class, Method, or File)
            scopeElements.add(psiElement)
        }
        else if (virtualFile != null) {
            // Fallback to File/Directory lookup
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            val psiDir = PsiManager.getInstance(project).findDirectory(virtualFile)
            if (psiFile != null) scopeElements.add(psiFile)
            if (psiDir != null) scopeElements.add(psiDir)
        }

        if (scopeElements.isEmpty()) return

        // 3. Run Analysis in Background
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Housekeeping Analysis ($mode)", true) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                // The Analyzer handles all UAST/Kotlin logic safely in the background
                val analyzer = HousekeepingAnalyzer(project)
                val unusedItems = analyzer.analyze(scopeElements, mode, indicator)

                ApplicationManager.getApplication().invokeLater {
                    showResults(project, unusedItems, mode)
                }
            }
        })
    }

    private fun showResults(project: Project, items: List<UnusedItem>, mode: AnalysisMode) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Housekeeping")
        toolWindow?.show {
            val content = toolWindow.contentManager.getContent(0)
            val component = content?.component
            if (component is HousekeepingToolWindowPanel) {
                component.updateResults(items)
                component.setInfoText("Showing results for: $mode")
            }
        }
    }
}

// --- Specific Action: Find Unused Methods ---
class FindUnusedMethodsAction : BaseHousekeepingAction(AnalysisMode.METHODS) {
    override fun update(e: AnActionEvent) {
        // SAFE CHECK: Only Strings and Standard VirtualFile
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isApplicable = vFile != null && (
                vFile.isDirectory ||
                        vFile.extension == "java" ||
                        vFile.extension == "kt"
                )
        e.presentation.isEnabledAndVisible = e.project != null && isApplicable
    }
}

// --- Specific Action: Find Unused Classes ---
class FindUnusedClassesAction : BaseHousekeepingAction(AnalysisMode.CLASSES) {
    override fun update(e: AnActionEvent) {
        // SAFE CHECK
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isApplicable = vFile != null && (
                vFile.isDirectory ||
                        vFile.extension == "java" ||
                        vFile.extension == "kt"
                )
        e.presentation.isEnabledAndVisible = e.project != null && isApplicable
    }
}

// --- Specific Action: Find Unused Resources ---
class FindUnusedResourcesAction : BaseHousekeepingAction(AnalysisMode.RESOURCES) {
    override fun update(e: AnActionEvent) {
        // SAFE CHECK
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        // Allow XML files OR any Directory (e.g. 'res' folder)
        val isApplicable = vFile != null && (
                vFile.extension == "xml" ||
                        vFile.isDirectory
                )
        e.presentation.isEnabledAndVisible = e.project != null && isApplicable
    }
}