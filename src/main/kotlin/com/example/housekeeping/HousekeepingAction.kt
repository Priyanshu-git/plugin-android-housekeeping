package com.example.housekeeping

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager

// --- Base Action (no longer DumbAware — disabled automatically during indexing) ---
abstract class BaseHousekeepingAction(
    private val mode: AnalysisMode,
    private val allowedExtensions: Set<String>
) : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isApplicable = vFile != null && (
                vFile.isDirectory || vFile.extension in allowedExtensions
                )
        e.presentation.isEnabledAndVisible = e.project != null && isApplicable
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Guard against invocation during indexing
        if (DumbService.isDumb(project)) {
            DumbService.getInstance(project).showDumbModeNotification(
                "Housekeeping analysis requires indexing to complete first."
            )
            return
        }

        // Determine scope with PSI safety
        val scopeElements = ReadAction.compute<List<PsiElement>, Throwable> {
            val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
            val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
            val elements = mutableListOf<PsiElement>()

            if (psiElement != null && psiElement !is PsiDirectory) {
                elements.add(psiElement)
            } else if (virtualFile != null) {
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                val psiDir = PsiManager.getInstance(project).findDirectory(virtualFile)
                if (psiFile != null) elements.add(psiFile)
                if (psiDir != null) elements.add(psiDir)
            }
            elements
        }

        if (scopeElements.isEmpty()) return

        // Show loading state immediately
        showLoadingState(project)

        // Run analysis in background with cancellation support
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Housekeeping: Finding Unused ${mode.displayName}",
            true
        ) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                val analyzer = HousekeepingAnalyzer(project)
                val unusedItems = analyzer.analyze(scopeElements, mode, indicator)

                ApplicationManager.getApplication().invokeLater {
                    showResults(project, unusedItems, mode)
                }
            }
        })
    }

    private fun showLoadingState(project: Project) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Housekeeping")
        toolWindow?.show {
            val content = toolWindow.contentManager.getContent(0)
            val component = content?.component
            if (component is HousekeepingToolWindowPanel) {
                component.showLoading(mode)
            }
        }
    }

    private fun showResults(project: Project, items: List<UnusedItem>, mode: AnalysisMode) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Housekeeping")
        toolWindow?.show {
            val content = toolWindow.contentManager.getContent(0)
            val component = content?.component
            if (component is HousekeepingToolWindowPanel) {
                component.updateResults(items, mode)
            }
        }
    }
}

// --- Concrete Actions ---

class FindUnusedMethodsAction : BaseHousekeepingAction(
    AnalysisMode.METHODS,
    setOf("java", "kt")
)

class FindUnusedClassesAction : BaseHousekeepingAction(
    AnalysisMode.CLASSES,
    setOf("java", "kt")
)

class FindUnusedResourcesAction : BaseHousekeepingAction(
    AnalysisMode.RESOURCES,
    setOf("xml")
)
