package com.example.housekeeping.core.action

import com.example.housekeeping.core.analysis.AnalysisEngine
import com.example.housekeeping.core.model.AnalysisMode
import com.example.housekeeping.core.model.UnusedItem
import com.example.housekeeping.core.spi.LanguageAnalyzer
import com.example.housekeeping.core.spi.ResourceAnalyzer
import com.example.housekeeping.core.ui.HousekeepingToolWindowPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager

abstract class BaseHousekeepingAction(private val mode: AnalysisMode) : AnAction() {

    /**
     * Declares that [update] should run on a background thread, not the EDT.
     */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /**
     * Controls action visibility: enabled only when a project is open and the selected file is
     * a directory or has an extension supported by a registered analyzer.
     */
    override fun update(e: AnActionEvent) {
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val allowedExtensions = getSupportedExtensions()
        val isApplicable = vFile != null && (
            vFile.isDirectory || vFile.extension in allowedExtensions
        )
        e.presentation.isEnabledAndVisible = e.project != null && isApplicable
    }

    /**
     * Main entry point: guards against dumb mode, resolves the user's selection into PSI scope
     * elements, shows the loading UI, and launches background analysis via [AnalysisEngine].
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        if (DumbService.isDumb(project)) {
            DumbService.getInstance(project).showDumbModeNotification(
                "Housekeeping analysis requires indexing to complete first."
            )
            return
        }

        val scopeElements = ReadAction.compute<List<PsiElement>, Throwable> {
            val psiManager = PsiManager.getInstance(project)
            val elements = mutableListOf<PsiElement>()

            val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
            if (virtualFiles != null && virtualFiles.isNotEmpty()) {
                for (vf in virtualFiles) {
                    if (vf.isDirectory) {
                        psiManager.findDirectory(vf)?.let { elements.add(it) }
                    } else {
                        psiManager.findFile(vf)?.let { elements.add(it) }
                    }
                }
            }

            if (elements.isEmpty()) {
                val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
                if (psiElement != null && psiElement !is PsiDirectory) {
                    elements.add(psiElement)
                }
            }

            elements
        }

        if (scopeElements.isEmpty()) return

        showLoadingState(project)

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Housekeeping: Finding Unused ${mode.displayName}",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                val engine = AnalysisEngine.getInstance(project)
                val unusedItems = engine.analyze(scopeElements, mode, indicator)

                ApplicationManager.getApplication().invokeLater {
                    showResults(project, unusedItems, mode)
                }
            }
        })
    }

    /**
     * Queries registered extension points at runtime to determine which file types this action
     * should be enabled for. Used by [update] to control menu-item visibility.
     *
     * Based on the current [mode]:
     * - [AnalysisMode.RESOURCES] → resolves all [ResourceAnalyzer] implementations from
     *   [ResourceAnalyzer.EP_NAME] (e.g. [AndroidResourceAnalyzer][com.example.housekeeping.overlays.android.AndroidResourceAnalyzer] → `{"xml"}`).
     * - [AnalysisMode.METHODS] / [AnalysisMode.CLASSES] → resolves all [LanguageAnalyzer]
     *   implementations from [LanguageAnalyzer.EP_NAME] (e.g. [UastLanguageAnalyzer][com.example.housekeeping.adapters.uast.UastLanguageAnalyzer] → `{"java", "kt"}`).
     *
     * Each analyzer's [supportedExtensions][LanguageAnalyzer.supportedExtensions] are merged into
     * a single deduplicated set. This means registering a new analyzer (e.g. a future
     * `PythonLanguageAnalyzer` with `{"py"}`) automatically enables the action on `.py` files
     * without any changes to action code.
     */
    private fun getSupportedExtensions(): Set<String> {
        return if (mode == AnalysisMode.RESOURCES) {
            ResourceAnalyzer.EP_NAME.extensionList.flatMap { it.supportedExtensions }.toSet()
        } else {
            LanguageAnalyzer.EP_NAME.extensionList.flatMap { it.supportedExtensions }.toSet()
        }
    }

    /** Makes the Housekeeping tool window visible (first-time activation) and switches
     *  the panel to a loading/spinner state for the current analysis mode. */
    private fun showLoadingState(project: Project) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Housekeeping")
        toolWindow?.isAvailable = true
        toolWindow?.show {
            val content = toolWindow.contentManager.getContent(0)
            val component = content?.component
            if (component is HousekeepingToolWindowPanel) {
                component.showLoading(mode)
            }
        }
    }

    /** Pushes the completed analysis results to the tool window panel so the user
     *  can review, select, and delete unused items. Called on the EDT via invokeLater. */
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
