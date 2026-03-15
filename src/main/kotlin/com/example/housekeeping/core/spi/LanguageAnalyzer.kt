package com.example.housekeeping.core.spi

import com.example.housekeeping.core.model.AnalysisMode
import com.example.housekeeping.core.model.UnusedItem
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

interface LanguageAnalyzer {

    val supportedExtensions: Set<String>

    fun analyzeFile(
        file: PsiFile,
        mode: AnalysisMode,
        context: AnalysisContext,
        results: MutableList<UnusedItem>
    )

    fun analyzeElement(
        element: PsiElement,
        mode: AnalysisMode,
        context: AnalysisContext,
        results: MutableList<UnusedItem>
    )

    companion object {
        @JvmField
        val EP_NAME = ExtensionPointName<LanguageAnalyzer>("com.nexxlabs.housekeeping.languageAnalyzer")
    }
}
