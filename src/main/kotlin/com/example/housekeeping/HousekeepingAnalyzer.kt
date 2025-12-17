package com.example.housekeeping

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.xml.XmlFile
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastVisibility
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType

enum class AnalysisMode {
    METHODS, CLASSES, RESOURCES
}

class HousekeepingAnalyzer(private val project: Project) {

    fun analyze(
        scopeElements: List<PsiElement>,
        mode: AnalysisMode,
        indicator: ProgressIndicator
    ): List<UnusedItem> {
        val results = mutableListOf<UnusedItem>()
        val total = scopeElements.size

        scopeElements.forEachIndexed { index, element ->
            if (indicator.isCanceled) return results
            indicator.fraction = index.toDouble() / total
            indicator.text = "Analyzing ${element.containingFile?.name ?: "element"}..."

            ReadAction.run<Throwable> {
                // 1. Try to convert selection to UAST element first
                val uElement = element.toUElement()

                when {
                    // --- Selection is a Method ---
                    uElement is UMethod -> {
                        if (mode == AnalysisMode.METHODS) analyzeMethod(uElement, results)
                    }

                    // --- Selection is a Class ---
                    uElement is UClass -> {
                        if (mode == AnalysisMode.CLASSES) {
                            analyzeClass(uElement, results)
                        }
                        if (mode == AnalysisMode.METHODS) {
                            uElement.methods.forEach { analyzeMethod(it, results) }
                        }
                    }

                    // --- Selection is a File (Java or Kotlin) ---
                    // UFile covers both PsiJavaFile and KtFile
                    uElement is UFile -> {
                        analyzeUFile(uElement, mode, results)
                    }

                    // --- Selection is an XML File (Resource) ---
                    element is XmlFile -> {
                        if (mode == AnalysisMode.RESOURCES) analyzeResourceFile(element, results)
                    }

                    // --- Selection is a Directory ---
                    element is PsiDirectory -> {
                        analyzeDirectory(element, mode, results, indicator)
                    }
                }
            }
        }
        return results
    }

    private fun analyzeDirectory(
        directory: PsiDirectory,
        mode: AnalysisMode,
        results: MutableList<UnusedItem>,
        indicator: ProgressIndicator
    ) {
        directory.files.forEach { file ->
            if (indicator.isCanceled) return

            // Convert PsiFile -> UFile (Works for Java & Kotlin)
            val uFile = file.toUElementOfType<UFile>()

            if (uFile != null) {
                // It is a code file
                analyzeUFile(uFile, mode, results)
            } else if (file is XmlFile && mode == AnalysisMode.RESOURCES) {
                // It is a resource file
                analyzeResourceFile(file, results)
            }
        }

        directory.subdirectories.forEach { subDir ->
            analyzeDirectory(subDir, mode, results, indicator)
        }
    }

    private fun analyzeUFile(uFile: UFile, mode: AnalysisMode, results: MutableList<UnusedItem>) {
        // UFile.classes includes:
        // 1. Regular classes
        // 2. Kotlin "Facade" classes (containers for top-level functions)

        uFile.classes.forEach { uClass ->
            if (mode == AnalysisMode.CLASSES) {
                // We typically don't want to flag the synthetic "UtilsKt" class as unused,
                // we only want to flag explicit user classes.
                // Facade classes usually have the file itself as sourcePsi, or null.
                val isUserDefinedClass = uClass.sourcePsi is PsiClass || uClass.sourcePsi is KtClass
                if (isUserDefinedClass) {
                    analyzeClass(uClass, results)
                }
            }

            if (mode == AnalysisMode.METHODS) {
                uClass.methods.forEach { uMethod ->
                    // Exclude synthetic methods (like component1() for data classes) if needed,
                    // but usually standard UAST methods are what we want.
                    analyzeMethod(uMethod, results)
                }
            }
        }
    }

    // --- 7.1 Method Analysis (UAST) ---
    private fun analyzeMethod(method: UMethod, results: MutableList<UnusedItem>) {
        // We need the JavaPsi (LightMethod) to usage searches
        val psiMethod = method.javaPsi
        val name = method.name

        // 1. Check Annotations
        if (hasKeepAnnotations(method)) return

        // 2. Check Overriding / Overridden
        // UAST doesn't have a direct "isOverriding" check, we use the PSI bridge.
        if (psiMethod.findSuperMethods().isNotEmpty()) return // Is Overriding

        // Check if Overridden (expensive search)
        // We use the psiMethod which represents the declaration in the "Java View"
        if (OverridingMethodsSearch.search(psiMethod).findFirst() != null) return

        // 3. Find Usages
        val searchScope = GlobalSearchScope.projectScope(project)
        val query = ReferencesSearch.search(psiMethod, searchScope)

        if (query.findFirst() == null) {
            val visibility = getVisibility(method)

            // For Kotlin top-level functions, the parent might be the file
            val containerName = method.getContainingUClass()?.name ?: "File"

            results.add(
                UnusedItem(
                    method.sourcePsi ?: psiMethod, // Prefer source PSI for navigation
                    "$containerName.$name()",
                    method.sourcePsi?.containingFile?.virtualFile?.path ?: "",
                    ItemType.METHOD,
                    "No references found.\nVisibility: $visibility"
                )
            )
        }
    }

    // --- 7.2 Class Analysis (UAST) ---
    private fun analyzeClass(uClass: UClass, results: MutableList<UnusedItem>) {
        val name = uClass.name ?: return
        val psiClass = uClass.javaPsi

        // 1. Android Entry Point Check
        if (isAndroidEntryPoint(uClass)) return

        // 2. Annotations
        if (hasKeepAnnotations(uClass)) return

        // 3. Find Usages
        val searchScope = GlobalSearchScope.projectScope(project)
        val query = ReferencesSearch.search(psiClass, searchScope)

        if (query.findFirst() == null) {
            results.add(
                UnusedItem(
                    uClass.sourcePsi ?: psiClass,
                    name,
                    uClass.sourcePsi?.containingFile?.virtualFile?.path ?: "",
                    ItemType.CLASS,
                    "No code references found."
                )
            )
        }
    }

    // --- 7.3 Resource Analysis (PSI/XML - unchanged mostly) ---
    private fun analyzeResourceFile(file: XmlFile, results: MutableList<UnusedItem>) {
        val parentDirName = file.parent?.name ?: ""

        if (parentDirName.startsWith("values")) {
            analyzeValueResources(file, results)
        } else if (isResourceFolder(parentDirName)) {
            analyzeFileResource(file, results)
        }
    }

    private fun analyzeValueResources(file: XmlFile, results: MutableList<UnusedItem>) {
        val rootTag = file.rootTag ?: return
        rootTag.subTags.forEach { tag ->
            val name = tag.getAttributeValue("name") ?: return@forEach
            val type = tag.name
            if (isTrackedResourceType(type)) {
                if (!isStringUsed(name)) {
                    results.add(
                        UnusedItem(
                            tag,
                            "$type/$name",
                            file.virtualFile.path,
                            ItemType.RESOURCE,
                            "No usage found."
                        )
                    )
                }
            }
        }
    }

    private fun analyzeFileResource(file: XmlFile, results: MutableList<UnusedItem>) {
        val resourceName = file.virtualFile.nameWithoutExtension
        val folderType = file.parent?.name?.substringBefore("-") ?: "resource"

        if (!isStringUsed(resourceName)) {
            results.add(
                UnusedItem(
                    file,
                    "$folderType/$resourceName",
                    file.virtualFile.path,
                    ItemType.RESOURCE,
                    "No usage found."
                )
            )
        }
    }

    // --- Helpers ---

    private fun hasKeepAnnotations(element: UAnnotated): Boolean {
        val keepSet = setOf("Keep", "Inject", "Provides", "OnClick", "OnTouch", "GET", "POST", "BindingAdapter")
        return element.uAnnotations.any { uAnn ->
            // 1. Try fully qualified name (e.g. "androidx.annotation.Keep") -> "Keep"
            // 2. Fallback to raw text (e.g. "@Keep" or "Keep") if unresolved
            val name = uAnn.qualifiedName?.substringAfterLast(".")
                ?: uAnn.uastAnchor?.sourcePsi?.text?.trimStart('@')

            name != null && keepSet.any { k -> name.contains(k) }
        }
    }

    private fun getVisibility(element: UDeclaration): String {
        return when {
            element.visibility == UastVisibility.PRIVATE -> "private"
            element.visibility == UastVisibility.PUBLIC -> "public"
            element.visibility == UastVisibility.PROTECTED -> "protected"
            element.visibility == UastVisibility.PACKAGE_LOCAL -> "package-private"
            else -> "default"
        }
    }

    private fun isAndroidEntryPoint(uClass: UClass): Boolean {
        // UClass gives us easy access to super types
        val androidBases = setOf(
            "android.app.Activity",
            "androidx.fragment.app.Fragment",
            "android.app.Service",
            "android.content.BroadcastReceiver",
            "android.content.ContentProvider",
            "android.app.Application",
            "android.view.View",
            "android.view.ViewGroup"
        )

        // Check hierarchy (Basic check via supers list to avoid heavy resolution if possible,
        // but robust check requires resolution).
        return uClass.supers.any { cls ->
            cls.qualifiedName?.let { qName -> androidBases.contains(qName) } == true
        }
    }

    private fun isStringUsed(target: String): Boolean {
        val searchScope = GlobalSearchScope.projectScope(project)
        val helper = PsiSearchHelper.getInstance(project)
        val nothingFound = helper.processElementsWithWord(
            {_,_ -> false },
            searchScope,
            target,
            UsageSearchContext.ANY,
            true
        )
        return !nothingFound
    }

    private fun isResourceFolder(name: String): Boolean {
        val types = setOf("layout", "drawable", "anim", "animator", "menu", "raw", "xml", "mipmap")
        return types.any { name.startsWith(it) }
    }

    private fun isTrackedResourceType(tag: String): Boolean {
        return setOf("string", "color", "dimen", "style", "integer", "bool", "id").contains(tag)
    }
}