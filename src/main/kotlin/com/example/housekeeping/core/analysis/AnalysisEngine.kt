package com.example.housekeeping.core.analysis

import com.example.housekeeping.core.model.AnalysisMode
import com.example.housekeeping.core.model.UnusedItem
import com.example.housekeeping.core.spi.AnalysisContext
import com.example.housekeeping.core.spi.AnnotationFilter
import com.example.housekeeping.core.spi.EntryPointProvider
import com.example.housekeeping.core.spi.LanguageAnalyzer
import com.example.housekeeping.core.spi.ResourceAnalyzer
import com.example.housekeeping.core.spi.SyntheticFilter
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class AnalysisEngine(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(AnalysisEngine::class.java)

        fun getInstance(project: Project): AnalysisEngine {
            return project.getService(AnalysisEngine::class.java)
        }
    }

    fun analyze(
        scopeElements: List<PsiElement>,
        mode: AnalysisMode,
        indicator: ProgressIndicator
    ): List<UnusedItem> {
        val results = java.util.Collections.synchronizedList(mutableListOf<UnusedItem>())
        val context = buildContext()
        val total = scopeElements.size

        scopeElements.forEachIndexed { index, element ->
            ProgressManager.checkCanceled()
            indicator.fraction = index.toDouble() / total

            val fileName = ReadAction.compute<String, Throwable> {
                element.containingFile?.name ?: "element"
            }
            indicator.text = "Analyzing $fileName..."

            when (element) {
                is PsiDirectory -> analyzeDirectory(element, mode, context, results, indicator)
                else -> ReadAction.run<Throwable> {
                    when (element) {
                        is PsiFile -> analyzeFile(element, mode, context, results)
                        else -> analyzeElement(element, mode, context, results)
                    }
                }
            }
        }

        LOG.info("Housekeeping analysis complete: found ${results.size} unused ${mode.displayName.lowercase()}")
        return results
    }

    private fun buildContext(): AnalysisContext {
        val entryPointProviders = EntryPointProvider.EP_NAME.extensionList
            .filter { it.isApplicable(project) }
        val annotationFilters = AnnotationFilter.EP_NAME.extensionList
        val syntheticFilters = SyntheticFilter.EP_NAME.extensionList

        return AnalysisContext(project, entryPointProviders, annotationFilters, syntheticFilters)
    }

    private fun analyzeDirectory(
        directory: PsiDirectory,
        mode: AnalysisMode,
        context: AnalysisContext,
        results: MutableList<UnusedItem>,
        indicator: ProgressIndicator
    ) {
        val (files, subdirs) = ReadAction.compute<Pair<Array<PsiFile>, Array<PsiDirectory>>, Throwable> {
            directory.files to directory.subdirectories
        }

        files.forEach { file ->
            ProgressManager.checkCanceled()
            ReadAction.run<Throwable> {
                indicator.text = "Analyzing ${file.name}..."
                analyzeFile(file, mode, context, results)
            }
        }

        subdirs.forEach { subDir ->
            ProgressManager.checkCanceled()
            analyzeDirectory(subDir, mode, context, results, indicator)
        }
    }

    private fun analyzeFile(
        file: PsiFile,
        mode: AnalysisMode,
        context: AnalysisContext,
        results: MutableList<UnusedItem>
    ) {
        val extension = file.virtualFile?.extension ?: return

        if (mode == AnalysisMode.RESOURCES) {
            ResourceAnalyzer.EP_NAME.extensionList
                .filter { it.isApplicable(project) && extension in it.supportedExtensions }
                .forEach { it.analyzeFile(file, context, results) }
        } else {
            LanguageAnalyzer.EP_NAME.extensionList
                .filter { extension in it.supportedExtensions }
                .forEach { it.analyzeFile(file, mode, context, results) }
        }
    }

    private fun analyzeElement(
        element: PsiElement,
        mode: AnalysisMode,
        context: AnalysisContext,
        results: MutableList<UnusedItem>
    ) {
        val extension = element.containingFile?.virtualFile?.extension ?: return

        if (mode == AnalysisMode.RESOURCES) {
            val file = element.containingFile ?: return
            ResourceAnalyzer.EP_NAME.extensionList
                .filter { it.isApplicable(project) && extension in it.supportedExtensions }
                .forEach { it.analyzeFile(file, context, results) }
        } else {
            LanguageAnalyzer.EP_NAME.extensionList
                .filter { extension in it.supportedExtensions }
                .forEach { it.analyzeElement(element, mode, context, results) }
        }
    }
}
