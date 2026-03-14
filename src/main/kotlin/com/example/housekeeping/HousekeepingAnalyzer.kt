package com.example.housekeeping

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.xml.XmlFile
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastVisibility
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType

enum class AnalysisMode(val displayName: String) {
    METHODS("Methods"),
    CLASSES("Classes"),
    RESOURCES("Resources")
}

class HousekeepingAnalyzer(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(HousekeepingAnalyzer::class.java)

        private val KEEP_ANNOTATIONS = setOf(
            "Keep", "Inject", "Provides", "OnClick", "OnTouch",
            "GET", "POST", "BindingAdapter"
        )

        private val ANDROID_ENTRY_POINTS = setOf(
            "android.app.Activity", "androidx.fragment.app.Fragment",
            "android.app.Service", "android.content.BroadcastReceiver",
            "android.content.ContentProvider", "android.app.Application",
            "android.view.View", "android.view.ViewModel", "androidx.lifecycle.ViewModel"
        )

        private val RESOURCE_FOLDER_TYPES = setOf(
            "layout", "drawable", "anim", "animator", "menu", "raw", "xml", "mipmap"
        )

        private val TRACKED_RESOURCE_TYPES = setOf(
            "string", "color", "dimen", "style", "integer", "bool", "id"
        )

        private val SYNTHETIC_METHOD_NAMES = setOf(
            "values", "valueOf", "entries",
            "hashCode", "equals", "toString", "copy",
            "<init>", "<clinit>"
        )
    }

    fun analyze(
        scopeElements: List<PsiElement>,
        mode: AnalysisMode,
        indicator: ProgressIndicator
    ): List<UnusedItem> {
        // Synchronized defensively; currently single-threaded but safe against future parallelization
        val results = java.util.Collections.synchronizedList(mutableListOf<UnusedItem>())
        val total = scopeElements.size

        scopeElements.forEachIndexed { index, element ->
            ProgressManager.checkCanceled()
            indicator.fraction = index.toDouble() / total

            // Access containingFile inside ReadAction (thread safety)
            val fileName = ReadAction.compute<String, Throwable> {
                element.containingFile?.name ?: "element"
            }
            indicator.text = "Analyzing $fileName..."

            when (element) {
                is PsiDirectory -> {
                    // Directory analysis uses per-file ReadAction internally
                    analyzeDirectory(element, mode, results, indicator)
                }
                else -> {
                    ReadAction.run<Throwable> {
                        val uElement = element.toUElement()
                        when {
                            uElement is UMethod && mode == AnalysisMode.METHODS -> {
                                analyzeMethod(uElement, results)
                            }
                            uElement is UClass -> {
                                if (mode == AnalysisMode.CLASSES) analyzeClass(uElement, results)
                                if (mode == AnalysisMode.METHODS) {
                                    uElement.methods.forEach { method ->
                                        ProgressManager.checkCanceled()
                                        analyzeMethod(method, results)
                                    }
                                }
                            }
                            uElement is UFile -> {
                                analyzeUFile(uElement, mode, results)
                            }
                            element is XmlFile && mode == AnalysisMode.RESOURCES -> {
                                analyzeResourceFile(element, results)
                            }
                        }
                    }
                }
            }
        }

        LOG.info("Housekeeping analysis complete: found ${results.size} unused ${mode.displayName.lowercase()}")
        return results
    }

    private fun analyzeDirectory(
        directory: PsiDirectory,
        mode: AnalysisMode,
        results: MutableList<UnusedItem>,
        indicator: ProgressIndicator
    ) {
        // Read directory contents in a short ReadAction, then process each file separately
        val (files, subdirs) = ReadAction.compute<Pair<Array<com.intellij.psi.PsiFile>, Array<PsiDirectory>>, Throwable> {
            directory.files to directory.subdirectories
        }

        files.forEach { file ->
            ProgressManager.checkCanceled()
            ReadAction.run<Throwable> {
                indicator.text = "Analyzing ${file.name}..."
                val uFile = file.toUElementOfType<UFile>()
                if (uFile != null) {
                    analyzeUFile(uFile, mode, results)
                } else if (file is XmlFile && mode == AnalysisMode.RESOURCES) {
                    analyzeResourceFile(file, results)
                }
            }
        }

        subdirs.forEach { subDir ->
            ProgressManager.checkCanceled()
            analyzeDirectory(subDir, mode, results, indicator)
        }
    }

    private fun analyzeUFile(uFile: UFile, mode: AnalysisMode, results: MutableList<UnusedItem>) {
        uFile.classes.forEach { uClass ->
            ProgressManager.checkCanceled()

            if (mode == AnalysisMode.CLASSES) {
                // Detect synthetic facade classes: sourcePsi is null or is the file itself
                val isSynthetic = uClass.sourcePsi == null || uClass.sourcePsi is com.intellij.psi.PsiFile
                if (!isSynthetic) {
                    analyzeClass(uClass, results)
                }
            }

            if (mode == AnalysisMode.METHODS) {
                uClass.methods.forEach { uMethod ->
                    ProgressManager.checkCanceled()
                    // Filter compiler-generated methods
                    val isSynthetic = uMethod.sourcePsi == null
                            || uMethod.name in SYNTHETIC_METHOD_NAMES
                            || uMethod.name.startsWith("component")
                            || uMethod.name.startsWith("copy\$default")
                    if (!isSynthetic) {
                        analyzeMethod(uMethod, results)
                    }
                }
            }
        }
    }

    private fun analyzeMethod(method: UMethod, results: MutableList<UnusedItem>) {
        val psiMethod = method.javaPsi
        val name = method.name

        // Skip main() entry points
        if (name == "main") return

        // Check annotations
        if (hasKeepAnnotations(method)) return

        // Check overriding / overridden
        if (psiMethod.findSuperMethods().isNotEmpty()) return

        if (OverridingMethodsSearch.search(psiMethod).findFirst() != null) return

        // Find usages
        val searchScope = GlobalSearchScope.projectScope(project)
        val query = ReferencesSearch.search(psiMethod, searchScope)

        if (query.findFirst() == null) {
            val visibility = getVisibility(method)
            val sourcePsi = method.sourcePsi ?: psiMethod
            results.add(
                UnusedItem(
                    SmartPointerManager.getInstance(project).createSmartPsiElementPointer(sourcePsi),
                    "$name()",
                    sourcePsi.containingFile?.virtualFile?.path ?: "",
                    ItemType.METHOD,
                    "No references found.\nVisibility: $visibility"
                )
            )
        }
    }

    private fun analyzeClass(uClass: UClass, results: MutableList<UnusedItem>) {
        val name = uClass.name ?: return
        val psiClass = uClass.javaPsi

        // Android entry point check (full hierarchy via InheritanceUtil)
        if (isAndroidEntryPoint(uClass)) return

        // Annotations
        if (hasKeepAnnotations(uClass)) return

        // Find usages
        val searchScope = GlobalSearchScope.projectScope(project)
        val query = ReferencesSearch.search(psiClass, searchScope)

        if (query.findFirst() == null) {
            val sourcePsi = uClass.sourcePsi ?: psiClass
            results.add(
                UnusedItem(
                    SmartPointerManager.getInstance(project).createSmartPsiElementPointer(sourcePsi),
                    name,
                    sourcePsi.containingFile?.virtualFile?.path ?: "",
                    ItemType.CLASS,
                    "No code references found."
                )
            )
        }
    }

    private fun analyzeResourceFile(file: XmlFile, results: MutableList<UnusedItem>) {
        val parentDirName = file.parent?.name ?: ""

        if (parentDirName.startsWith("values")) {
            analyzeValueResources(file, results)
        } else if (isResourceFolder(parentDirName)) {
            analyzeFileResource(file, results)
        }
    }

    private fun analyzeValueResources(file: XmlFile, results: MutableList<UnusedItem>) {
        val vf = file.virtualFile ?: return
        val rootTag = file.rootTag ?: return
        rootTag.subTags.forEach { tag ->
            ProgressManager.checkCanceled()
            val name = tag.getAttributeValue("name") ?: return@forEach
            val type = tag.name
            if (isTrackedResourceType(type)) {
                if (!isStringUsed(name)) {
                    results.add(
                        UnusedItem(
                            SmartPointerManager.getInstance(project).createSmartPsiElementPointer(tag),
                            "$type/$name",
                            vf.path,
                            ItemType.RESOURCE,
                            "No usage of '@$type/$name' or 'R.$type.$name' found."
                        )
                    )
                }
            }
        }
    }

    private fun analyzeFileResource(file: XmlFile, results: MutableList<UnusedItem>) {
        val vf = file.virtualFile ?: return
        val resourceName = vf.nameWithoutExtension
        val folderType = file.parent?.name?.substringBefore("-") ?: "resource"

        if (!isStringUsed(resourceName)) {
            results.add(
                UnusedItem(
                    SmartPointerManager.getInstance(project).createSmartPsiElementPointer(file),
                    "$folderType/$resourceName",
                    vf.path,
                    ItemType.RESOURCE,
                    "No usage of '$resourceName' found in code or XML."
                )
            )
        }
    }

    // --- Helpers ---

    private fun hasKeepAnnotations(element: UAnnotated): Boolean {
        return element.uAnnotations.any { uAnn ->
            val name = uAnn.qualifiedName?.substringAfterLast(".")
                ?: uAnn.uastAnchor?.sourcePsi?.text?.trimStart('@')
            name != null && KEEP_ANNOTATIONS.any { k -> name.contains(k) }
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
        val psiClass = uClass.javaPsi
        // Use InheritanceUtil to traverse the full class hierarchy
        return ANDROID_ENTRY_POINTS.any { baseFqn ->
            InheritanceUtil.isInheritor(psiClass, baseFqn)
        }
    }

    private fun isStringUsed(target: String): Boolean {
        val searchScope = GlobalSearchScope.projectScope(project)
        val helper = PsiSearchHelper.getInstance(project)
        return !helper.processElementsWithWord({ _, _ -> false }, searchScope, target, UsageSearchContext.ANY, true)
    }

    private fun isResourceFolder(name: String): Boolean {
        return RESOURCE_FOLDER_TYPES.any { name.startsWith(it) }
    }

    private fun isTrackedResourceType(tag: String): Boolean {
        return TRACKED_RESOURCE_TYPES.contains(tag)
    }
}
