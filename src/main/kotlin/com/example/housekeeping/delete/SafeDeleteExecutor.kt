package com.example.housekeeping.delete

import com.example.housekeeping.analysis.AnalysisResult
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.refactoring.safeDelete.SafeDeleteHandler

class SafeDeleteExecutor(private val project: Project) {

    fun delete(items: List<AnalysisResult>) {
        WriteCommandAction.runWriteCommandAction(project) {
            items.forEach {
                when (it) {
                    is AnalysisResult.MethodResult ->
                        SafeDeleteHandler.invoke(project, arrayOf(it.method), false)
                    is AnalysisResult.ClassResult ->
                        SafeDeleteHandler.invoke(project, arrayOf(it.clazz), false)
                    is AnalysisResult.ResourceResult ->
                        it.file.delete(this)
                }
            }
        }
    }
}
