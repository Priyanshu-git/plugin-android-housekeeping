package com.example.housekeeping

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.xml.XmlFile
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastVisibility
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
                // Try UAST conversion first
                val uElement = element.toUElement()

                when {
                    // --- Selection is a Method ---
                    uElement is UMethod -> {
                        if (mode == AnalysisMode.METHODS) analyzeMethod(uElement, results)
                    }

                    // --- Selection is a Class ---
                    uElement is UClass -> {
                        if (mode == AnalysisMode.CLASSES) analyzeClass(uElement, results)
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

            // UFile works for both Java and Kotlin
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
        // uFile.classes returns all classes in the file
        // For Kotlin, this includes the "Facade" class (e.g. FileNameKt) which holds top-level functions
        uFile.classes.forEach { uClass ->

            // 1. Analyze the Class itself (skip synthetic facade classes)
            if (mode == AnalysisMode.CLASSES) {
                // Heuristic: User classes usually have a physical PsiClass/KtClass source
                // Facades often map weirdly, but usually valid user classes have names.
                val isSynthetic = uClass.name?.endsWith("Kt") == true && uClass.methods.all { it.isStatic }
                if (!isSynthetic) {
                    analyzeClass(uClass, results)
                }
            }

            // 2. Analyze Methods inside (including Top-Level functions which appear in Facade classes)
            if (mode == AnalysisMode.METHODS) {
                uClass.methods.forEach { uMethod ->
                    // Filter out standard synthetic methods (like main, values, valueOf)
                    val isSynthetic = uMethod.name in setOf("values", "valueOf", "component1", "copy")
                    if (!isSynthetic) {
                        analyzeMethod(uMethod, results)
                    }
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
            results.add(
                UnusedItem(
                    method.sourcePsi ?: psiMethod,
                    "$name()",
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

        // Strategy A: Value Resources (strings.xml, colors.xml in values/)
        if (parentDirName.startsWith("values")) {
            analyzeValueResources(file, results)

            // Strategy B: File Resources (layout/abc.xml, drawable/xyz.xml)
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
                            tag,    // Link to the specific tag, not the file
                            "$type/$name",
                            file.virtualFile.path,
                            ItemType.RESOURCE,
                            "No usage of '@$type/$name' or 'R.$type.$name' found."
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
                    "No usage of '$resourceName' found in Code or XML."
                )
            )
        }
    }

    // --- Helpers ---
    private fun hasKeepAnnotations(element: UAnnotated): Boolean {
        val keepSet = setOf("Keep", "Inject", "Provides", "OnClick", "OnTouch", "GET", "POST", "BindingAdapter")
        return element.uAnnotations.any { uAnn ->
            val name = uAnn.qualifiedName?.substringAfterLast(".")
                ?: uAnn.uastAnchor?.sourcePsi?.text?.trimStart('@')
            name != null && keepSet.any { k -> name.contains(k) }
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

    private fun isAndroidEntryPoint(uClass: UClass): Boolean {
        val androidBases = setOf(
            "android.app.Activity", "androidx.fragment.app.Fragment",
            "android.app.Service", "android.content.BroadcastReceiver",
            "android.content.ContentProvider", "android.app.Application",
            "android.view.View", "android.view.ViewModel", "androidx.lifecycle.ViewModel"
        )

        // Check hierarchy (Basic check via supers list to avoid heavy resolution if possible,
        // but robust check requires resolution).
        return uClass.supers.any { cls ->
            cls.qualifiedName?.let { qName -> androidBases.contains(qName) } == true
        }
    }

    private fun isStringUsed(target: String): Boolean {
        // Optimistic Text Search (Heuristic)
        // Scans project for the string. Effective for R.type.name, @type/name, etc.
        val searchScope = GlobalSearchScope.projectScope(project)
        val helper = PsiSearchHelper.getInstance(project)
        return !helper.processElementsWithWord({_,_ -> false }, searchScope, target, UsageSearchContext.ANY, true)
    }

    private fun isResourceFolder(name: String): Boolean {
        val types = setOf("layout", "drawable", "anim", "animator", "menu", "raw", "xml", "mipmap")
        return types.any { name.startsWith(it) }
    }

    private fun isTrackedResourceType(tag: String): Boolean {
        return setOf("string", "color", "dimen", "style", "integer", "bool", "id").contains(tag)
    }
}