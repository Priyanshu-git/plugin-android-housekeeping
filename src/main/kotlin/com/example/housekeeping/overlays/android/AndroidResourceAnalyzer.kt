package com.example.housekeeping.overlays.android

import com.example.housekeeping.core.model.ItemType
import com.example.housekeeping.core.model.UnusedItem
import com.example.housekeeping.core.spi.AnalysisContext
import com.example.housekeeping.core.spi.ResourceAnalyzer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.xml.XmlFile

class AndroidResourceAnalyzer : ResourceAnalyzer {

    companion object {
        private val RESOURCE_FOLDER_TYPES = setOf(
            "layout", "drawable", "anim", "animator", "menu", "raw", "xml", "mipmap"
        )

        private val TRACKED_RESOURCE_TYPES = setOf(
            "string", "color", "dimen", "style", "integer", "bool", "id"
        )
    }

    override val supportedExtensions = setOf("xml")

    override fun isApplicable(project: Project): Boolean = true

    override fun analyzeFile(
        file: PsiFile,
        context: AnalysisContext,
        results: MutableList<UnusedItem>
    ) {
        if (file !is XmlFile) return
        val parentDirName = file.parent?.name ?: ""

        if (parentDirName.startsWith("values")) {
            analyzeValueResources(file, context, results)
        } else if (isResourceFolder(parentDirName)) {
            analyzeFileResource(file, context, results)
        }
    }

    private fun analyzeValueResources(
        file: XmlFile,
        context: AnalysisContext,
        results: MutableList<UnusedItem>
    ) {
        val vf = file.virtualFile ?: return
        val rootTag = file.rootTag ?: return
        rootTag.subTags.forEach { tag ->
            ProgressManager.checkCanceled()
            val name = tag.getAttributeValue("name") ?: return@forEach
            val type = tag.name
            if (isTrackedResourceType(type)) {
                if (!isResourceUsed(name, type, context)) {
                    results.add(
                        UnusedItem(
                            SmartPointerManager.getInstance(context.project).createSmartPsiElementPointer(tag),
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

    private fun analyzeFileResource(
        file: XmlFile,
        context: AnalysisContext,
        results: MutableList<UnusedItem>
    ) {
        val vf = file.virtualFile ?: return
        val resourceName = vf.nameWithoutExtension
        val folderType = file.parent?.name?.substringBefore("-") ?: "resource"

        if (!isResourceUsed(resourceName, folderType, context)) {
            results.add(
                UnusedItem(
                    SmartPointerManager.getInstance(context.project).createSmartPsiElementPointer(file),
                    "$folderType/$resourceName",
                    vf.path,
                    ItemType.RESOURCE,
                    "No usage of '$resourceName' found in code or XML."
                )
            )
        }
    }

    /**
     * Returns true if [resourceName] of [resourceType] appears to be referenced somewhere in the
     * project. Two passes are made, each with a context-validating processor that rejects matches
     * that are clearly not Android resource references:
     *
     *  - **Code pass** (`IN_CODE`): accepts a match only when an ancestor element's text contains
     *    the qualified pattern `R.<type>.<name>`, ruling out unrelated identifiers that share the
     *    resource name.
     *  - **XML pass** (`ANY`): accepts a match only when an ancestor's text contains
     *    `@<type>/<name>` or `?<type>/<name>`, ruling out attribute values that merely contain the
     *    word but are not resource references.
     */
    private fun isResourceUsed(resourceName: String, resourceType: String, context: AnalysisContext): Boolean {
        val searchScope = GlobalSearchScope.projectScope(context.project)
        val helper = PsiSearchHelper.getInstance(context.project)
        var found = false

        // --- Code references: R.resourceType.resourceName ---
        helper.processElementsWithWord({ element, _ ->
            val isRRef = generateSequence(element) { it.parent }
                .take(4)
                .any { it.text.contains("R.$resourceType.$resourceName") }
            if (isRRef) { found = true; false } else true
        }, searchScope, resourceName, UsageSearchContext.IN_CODE, true)

        if (found) return true

        // --- XML attribute references: @resourceType/resourceName or ?resourceType/resourceName ---
        helper.processElementsWithWord({ element, _ ->
            val isXmlRef = generateSequence(element) { it.parent }
                .take(3)
                .any { node ->
                    val t = node.text
                    t.contains("@$resourceType/$resourceName") ||
                        t.contains("?$resourceType/$resourceName")
                }
            if (isXmlRef) { found = true; false } else true
        }, searchScope, resourceName, UsageSearchContext.ANY, true)

        return found
    }

    private fun isResourceFolder(name: String): Boolean {
        return RESOURCE_FOLDER_TYPES.any { name.startsWith(it) }
    }

    private fun isTrackedResourceType(tag: String): Boolean {
        return TRACKED_RESOURCE_TYPES.contains(tag)
    }
}
