package com.example.housekeeping.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

abstract class BaseHousekeepingAction : AnAction() {

    final override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = isApplicable(e)
    }

    protected abstract fun isApplicable(e: AnActionEvent): Boolean

    protected fun project(e: AnActionEvent): Project =
        e.project ?: error("Project required")
}
