package com.example.housekeeping.analysis

import com.example.housekeeping.util.AndroidUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class ResourceAnalyzer(private val project: Project) {

    fun analyze(e: AnActionEvent): List<AnalysisResult> {
        val root = AndroidUtil.getSelectedResRoot(e) ?: return emptyList()

        val resources = mutableListOf<VirtualFile>()
        collectResources(root, resources)

        return resources
            .filter { AndroidUtil.isUnusedResource(project, it) }
            .map {
                AnalysisResult.ResourceResult(
                    file = it,
                    reason = "No references found in code or XML"
                )
            }
    }

    private fun collectResources(dir: VirtualFile, out: MutableList<VirtualFile>) {
        if (dir.isDirectory) {
            dir.children.forEach { collectResources(it, out) }
        } else {
            out += dir
        }
    }
}
