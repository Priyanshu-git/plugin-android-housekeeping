package com.example.housekeeping.adapters.uast

import com.example.housekeeping.core.model.AnalysisMode
import com.example.housekeeping.core.model.ItemType
import com.example.housekeeping.core.model.UnusedItem
import com.example.housekeeping.core.spi.AnalysisContext
import com.example.housekeeping.core.spi.LanguageAnalyzer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastVisibility
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType

class UastLanguageAnalyzer : LanguageAnalyzer {

    override val supportedExtensions = setOf("java", "kt")

    override fun analyzeFile(
        file: PsiFile,
        mode: AnalysisMode,
        context: AnalysisContext,
        results: MutableList<UnusedItem>
    ) {
        val uFile = file.toUElementOfType<UFile>() ?: return
        analyzeUFile(uFile, mode, context, results)
    }

    override fun analyzeElement(
        element: PsiElement,
        mode: AnalysisMode,
        context: AnalysisContext,
        results: MutableList<UnusedItem>
    ) {
        val uElement = element.toUElement() ?: return
        when {
            uElement is UMethod && mode == AnalysisMode.METHODS -> {
                analyzeMethod(uElement, context, results)
            }
            uElement is UClass -> {
                if (mode == AnalysisMode.CLASSES) analyzeClass(uElement, context, results)
                if (mode == AnalysisMode.METHODS) {
                    uElement.methods.forEach { method ->
                        ProgressManager.checkCanceled()
                        analyzeMethod(method, context, results)
                    }
                }
            }
            uElement is UFile -> {
                analyzeUFile(uElement, mode, context, results)
            }
        }
    }

    private fun analyzeUFile(
        uFile: UFile,
        mode: AnalysisMode,
        context: AnalysisContext,
        results: MutableList<UnusedItem>
    ) {
        uFile.classes.forEach { uClass ->
            ProgressManager.checkCanceled()

            if (mode == AnalysisMode.CLASSES) {
                val isSynthetic = context.syntheticFilters.any { it.isSyntheticClass(uClass) }
                if (!isSynthetic) {
                    analyzeClass(uClass, context, results)
                }
            }

            if (mode == AnalysisMode.METHODS) {
                uClass.methods.forEach { uMethod ->
                    ProgressManager.checkCanceled()
                    val isSynthetic = context.syntheticFilters.any { it.isSyntheticMethod(uMethod) }
                    if (!isSynthetic) {
                        analyzeMethod(uMethod, context, results)
                    }
                }
            }
        }
    }

    private fun analyzeMethod(
        method: UMethod,
        context: AnalysisContext,
        results: MutableList<UnusedItem>
    ) {
        val psiMethod = method.javaPsi
        val name = method.name

        if (name == "main") return

        // Skip constructors of class types whose constructors are never called directly:
        // object (singleton), sealed (subclass-only), abstract (subclass-only),
        // enum (implicit), annotation (declarative usage only).
        if (method.isConstructor) {
            val containingClass = method.uastParent as? UClass
            if (containingClass != null) {
                val psiClass = containingClass.javaPsi
                if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)
                    || psiClass.isEnum
                    || psiClass.isAnnotationType
                    || containingClass.isSealed()
                    || containingClass.isObject()
                ) return
            }
        }

        if (context.annotationFilters.any { it.shouldKeep(method) }) return

        if (psiMethod.findSuperMethods().isNotEmpty()) return

        if (OverridingMethodsSearch.search(psiMethod).findFirst() != null) return

        val searchScope = GlobalSearchScope.projectScope(context.project)
        val query = ReferencesSearch.search(psiMethod, searchScope)

        if (query.findFirst() == null) {
            val visibility = getVisibility(method)
            val sourcePsi = method.sourcePsi ?: psiMethod
            results.add(
                UnusedItem(
                    SmartPointerManager.getInstance(context.project).createSmartPsiElementPointer(sourcePsi),
                    "$name()",
                    sourcePsi.containingFile?.virtualFile?.path ?: "",
                    ItemType.METHOD,
                    "No references found.\nVisibility: $visibility"
                )
            )
        }
    }

    private fun analyzeClass(
        uClass: UClass,
        context: AnalysisContext,
        results: MutableList<UnusedItem>
    ) {
        val name = uClass.name ?: return
        val psiClass = uClass.javaPsi

        if (context.entryPointProviders.any { it.isEntryPoint(psiClass) }) return

        if (context.annotationFilters.any { it.shouldKeep(uClass) }) return

        val searchScope = GlobalSearchScope.projectScope(context.project)
        val query = ReferencesSearch.search(psiClass, searchScope)

        if (query.findFirst() == null) {
            val sourcePsi = uClass.sourcePsi ?: psiClass
            results.add(
                UnusedItem(
                    SmartPointerManager.getInstance(context.project).createSmartPsiElementPointer(sourcePsi),
                    name,
                    sourcePsi.containingFile?.virtualFile?.path ?: "",
                    ItemType.CLASS,
                    "No code references found."
                )
            )
        }
    }

    private fun getVisibility(element: UDeclaration): String {
        return when (element.visibility) {
            UastVisibility.PRIVATE -> "private"
            UastVisibility.PUBLIC -> "public"
            UastVisibility.PROTECTED -> "protected"
            else -> "default"
        }
    }

    private fun UClass.isSealed(): Boolean {
        val ktClass = sourcePsi as? KtClass
        return ktClass?.hasModifier(KtTokens.SEALED_KEYWORD) == true
    }

    private fun UClass.isObject(): Boolean {
        return sourcePsi is KtObjectDeclaration
    }
}
