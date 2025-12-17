package com.example.housekeeping

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.xml.XmlFile
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

// --- Base Action ---
abstract class BaseHousekeepingAction(private val mode: AnalysisMode) : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        // Determine scope
        val scopeElements = mutableListOf<PsiElement>()

        if (psiElement != null) {
            scopeElements.add(psiElement)
        } else if (virtualFile != null) {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            val psiDir = PsiManager.getInstance(project).findDirectory(virtualFile)
            if (psiFile != null) scopeElements.add(psiFile)
            if (psiDir != null) scopeElements.add(psiDir)
        }

        if (scopeElements.isEmpty()) return

        // Run Analysis
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Housekeeping Analysis ($mode)", true) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                val analyzer = HousekeepingAnalyzer(project)
                // Pass the specific mode to the analyzer
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
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        // Enable if it's a Java/Kotlin Class, File, or Directory (Package)
        val isCodeContext = element is PsiClass || element is KtClass ||
                element is PsiJavaFile || element is KtFile ||
                (element is PsiDirectory) // allow searching in package

        e.presentation.isEnabledAndVisible = e.project != null && isCodeContext
    }
}

// --- Specific Action: Find Unused Classes ---
class FindUnusedClassesAction : BaseHousekeepingAction(AnalysisMode.CLASSES) {
    override fun update(e: AnActionEvent) {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)

        // Enable primarily on Packages (Directories)
        val isPackageContext = element is PsiDirectory || element is PsiPackage

        e.presentation.isEnabledAndVisible = e.project != null && isPackageContext
    }
}

// --- Specific Action: Find Unused Resources ---
class FindUnusedResourcesAction : BaseHousekeepingAction(AnalysisMode.RESOURCES) {
    override fun update(e: AnActionEvent) {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        // Enable if in 'res' directory or is an XML file
        val isResourceContext = (element is PsiDirectory && (vFile?.path?.contains("/res") == true)) ||
                (element is XmlFile)

        e.presentation.isEnabledAndVisible = e.project != null && isResourceContext
    }
}