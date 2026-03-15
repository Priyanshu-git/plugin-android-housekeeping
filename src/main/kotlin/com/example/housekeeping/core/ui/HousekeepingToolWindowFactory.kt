package com.example.housekeeping.core.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class HousekeepingToolWindowFactory : ToolWindowFactory {

    override fun shouldBeAvailable(project: Project) = false

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = HousekeepingToolWindowPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
