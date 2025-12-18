package com.example.housekeeping.util

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

object AndroidUtil {

    private val ENTRY_POINTS = listOf(
        "Activity",
        "Fragment",
        "Service",
        "BroadcastReceiver",
        "ContentProvider"
    )

    fun isAndroidEntryPoint(psiClass: PsiClass): Boolean {
        return ENTRY_POINTS.any {
            psiClass.superClass?.name?.contains(it) == true
        }
    }

    fun getSelectedResRoot(e: AnActionEvent): VirtualFile? {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        return if (file.path.contains("/res")) file else null
    }

    fun isUnusedResource(project: Project, file: VirtualFile): Boolean {
        val name = file.nameWithoutExtension
        val matches = FilenameIndex.getFilesByName(
            project,
            name,
            GlobalSearchScope.projectScope(project)
        )
        return matches.isEmpty()
    }
}
