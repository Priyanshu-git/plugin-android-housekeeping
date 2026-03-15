package com.example.housekeeping.core.spi

import com.example.housekeeping.core.model.UnusedItem
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

interface ResourceAnalyzer {

    val supportedExtensions: Set<String>

    fun isApplicable(project: Project): Boolean

    fun analyzeFile(
        file: PsiFile,
        context: AnalysisContext,
        results: MutableList<UnusedItem>
    )

    companion object {
        @JvmField
        val EP_NAME = ExtensionPointName<ResourceAnalyzer>("com.nexxlabs.housekeeping.resourceAnalyzer")
    }
}
