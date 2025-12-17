package com.example.housekeeping

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.xml.XmlFile
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction

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
                when (element) {
                    // Direct Selection Handling
                    is PsiMethod, is KtNamedFunction -> {
                        if (mode == AnalysisMode.METHODS) analyzeMethod(element, results)
                    }
                    is PsiClass -> {
                        if (mode == AnalysisMode.CLASSES) analyzeClass(element, results)
                        if (mode == AnalysisMode.METHODS) {
                            element.methods.forEach { analyzeMethod(it, results) }
                        }
                    }
                    is KtClass -> {
                        if (mode == AnalysisMode.CLASSES) analyzeClass(element, results)
                        if (mode == AnalysisMode.METHODS) {
                            element.declarations.filterIsInstance<KtNamedFunction>()
                                .forEach { analyzeMethod(it, results) }
                        }
                    }
                    is XmlFile -> {
                        if (mode == AnalysisMode.RESOURCES) analyzeResourceFile(element, results)
                    }
                    // Recursive Directory Traversal
                    is PsiDirectory -> {
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

            when (file) {
                // 1. Kotlin Files
                is KtFile -> {
                    val kClasses = file.declarations.filterIsInstance<KtClass>()

                    if (mode == AnalysisMode.CLASSES) {
                        kClasses.forEach { analyzeClass(it, results) }
                    }

                    if (mode == AnalysisMode.METHODS) {
                        // Class methods
                        kClasses.forEach { kClass ->
                            kClass.declarations.filterIsInstance<KtNamedFunction>()
                                .forEach { analyzeMethod(it, results) }
                        }
                        // Top-level functions
                        file.declarations.filterIsInstance<KtNamedFunction>()
                            .forEach { analyzeMethod(it, results) }
                    }
                }

                // 2. Java Files
                is PsiJavaFile -> {
                    val jClasses = file.classes

                    if (mode == AnalysisMode.CLASSES) {
                        jClasses.forEach { analyzeClass(it, results) }
                    }

                    if (mode == AnalysisMode.METHODS) {
                        jClasses.forEach { cls ->
                            cls.methods.forEach { analyzeMethod(it, results) }
                        }
                    }
                }

                // 3. Resource Files
                is XmlFile -> {
                    if (mode == AnalysisMode.RESOURCES) analyzeResourceFile(file, results)
                }
            }
        }

        directory.subdirectories.forEach { subDir ->
            analyzeDirectory(subDir, mode, results, indicator)
        }
    }

    // --- 7.1 Method Analysis ---
    private fun analyzeMethod(element: PsiElement, results: MutableList<UnusedItem>) {
        val name = (element as? PsiNameIdentifierOwner)?.name ?: return

        // 1. Check Annotations (@Keep, @OnClick, etc)
        if (hasKeepAnnotations(element)) return

        // 2. Check Overriding (Is this method overriding a super method?)
        if (isOverriding(element)) return

        // 3. Check Overridden (Is this method overridden by a subclass?)
        if (isOverridden(element)) return

        // 4. Find Usages (Code & XML via PSI)
        val searchScope = GlobalSearchScope.projectScope(project)
        val query = ReferencesSearch.search(element, searchScope)

        if (query.findFirst() == null) {
            val visibility = getVisibility(element)
            results.add(
                UnusedItem(
                    element,
                    "$name()",
                    element.containingFile?.virtualFile?.path ?: "",
                    ItemType.METHOD,
                    "No references found.\nVisibility: $visibility\n(Checked overrides & annotations)"
                )
            )
        }
    }

    // --- 7.2 Class Analysis ---
    private fun analyzeClass(element: PsiElement, results: MutableList<UnusedItem>) {
        val name = (element as? PsiNameIdentifierOwner)?.name ?: return

        // 1. Android Entry Point Check
        if (isAndroidEntryPoint(element)) return

        // 2. Annotations
        if (hasKeepAnnotations(element)) return

        // 3. Find Usages
        val searchScope = GlobalSearchScope.projectScope(project)
        val query = ReferencesSearch.search(element, searchScope)

        if (query.findFirst() == null) {
            results.add(
                UnusedItem(
                    element,
                    name,
                    element.containingFile?.virtualFile?.path ?: "",
                    ItemType.CLASS,
                    "No code references found.\n(Heuristics checked: Entry Point, Keep Annotations)"
                )
            )
        }
    }

    // --- 7.3 Resource Analysis ---
    private fun analyzeResourceFile(file: XmlFile, results: MutableList<UnusedItem>) {
        val parentDirName = file.parent?.name ?: ""

        // Strategy A: Value Resources (strings.xml, colors.xml in values/)
        if (parentDirName.startsWith("values")) {
            analyzeValueResources(file, results)
        }
        // Strategy B: File Resources (layout/abc.xml, drawable/xyz.xml)
        else if (isResourceFolder(parentDirName)) {
            analyzeFileResource(file, results)
        }
    }

    private fun analyzeValueResources(file: XmlFile, results: MutableList<UnusedItem>) {
        val rootTag = file.rootTag ?: return
        // Iterate over tags like <color name="primary">, <string name="app_name">
        rootTag.subTags.forEach { tag ->
            val name = tag.getAttributeValue("name") ?: return@forEach
            val type = tag.name // "string", "color", "dimen", "style"

            // Only analyze standard resource types
            if (isTrackedResourceType(type)) {
                if (!isStringUsed(name)) {
                    results.add(
                        UnusedItem(
                            tag, // Link to the specific tag, not the file
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

    private fun isStringUsed(target: String): Boolean {
        // Optimistic Text Search (Heuristic)
        // Scans project for the string. Effective for R.type.name, @type/name, etc.
        val searchScope = GlobalSearchScope.projectScope(project)
        val helper = PsiSearchHelper.getInstance(project)

        // processElementsWithWord returns FALSE if it found something and stopped.
        val nothingFound = helper.processElementsWithWord(
            {_,_ -> false }, // Stop processor immediately upon finding one occurrence
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

    // --- Helpers ---

    private fun hasKeepAnnotations(element: PsiElement): Boolean {
        val annotations = when (element) {
            is PsiModifierListOwner -> element.modifierList?.annotations?.map { it.qualifiedName }
            is KtNamedFunction -> element.annotationEntries.map { it.shortName?.asString() }
            is KtClass -> element.annotationEntries.map { it.shortName?.asString() }
            else -> emptyList()
        } ?: emptyList()

        val keepSet = setOf("Keep", "Inject", "Provides", "OnClick", "OnTouch", "GET", "POST", "BindingAdapter")
        return annotations.any { name -> name != null && keepSet.any { k -> name.contains(k) } }
    }

    private fun isOverriding(element: PsiElement): Boolean {
        if (element is PsiMethod) {
            return element.findSuperMethods().isNotEmpty()
        }
        if (element is KtNamedFunction) {
            return element.hasModifier(KtTokens.OVERRIDE_KEYWORD)
        }
        return false
    }

    private fun isOverridden(element: PsiElement): Boolean {
        // 1. Java check
        if (element is PsiMethod) {
            // This is expensive; it searches the hierarchy
            val overrides = OverridingMethodsSearch.search(element)
            return overrides.findFirst() != null
        }
        // 2. Kotlin check
        if (element is KtNamedFunction) {
            // Convert to Light Method to use Java Search infrastructure
            val lightMethods = element.toLightMethods()
            return lightMethods.any { method ->
                OverridingMethodsSearch.search(method).findFirst() != null
            }
        }
        return false
    }

    private fun getVisibility(element: PsiElement): String {
        if (element is PsiModifierListOwner) {
            if (element.hasModifierProperty(PsiModifier.PRIVATE)) return "private"
            if (element.hasModifierProperty(PsiModifier.PUBLIC)) return "public"
            if (element.hasModifierProperty(PsiModifier.PROTECTED)) return "protected"
        }
        if (element is KtModifierListOwner) {
            if (element.hasModifier(KtTokens.PRIVATE_KEYWORD)) return "private"
            if (element.hasModifier(KtTokens.PUBLIC_KEYWORD)) return "public"
            if (element.hasModifier(KtTokens.PROTECTED_KEYWORD)) return "protected"
            if (element.hasModifier(KtTokens.INTERNAL_KEYWORD)) return "internal"
        }
        return "default"
    }

    private fun isAndroidEntryPoint(element: PsiElement): Boolean {
        val supers = when(element) {
            is PsiClass -> element.supers.map { it.qualifiedName }
            is KtClass -> element.superTypeListEntries.map { it.text }
            else -> emptyList()
        }
        // Expanded list of Android entry points
        val androidBases = listOf(
            "Activity", "Fragment", "Service", "BroadcastReceiver",
            "ContentProvider", "Application", "View", "ViewGroup",
            "android.app.Activity", "androidx.fragment.app.Fragment",
            "android.view.View"
        )
        return supers.any { s -> s != null && androidBases.any { base -> s.contains(base) } }
    }
}