package com.example.housekeeping.ui

import com.example.housekeeping.analysis.AnalysisResult
import com.example.housekeeping.delete.SafeDeleteExecutor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel

class HousekeepingPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val model = ResultsModel()
    private val list = JList(model).apply {
        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    }



    init {
        add(JScrollPane(list), BorderLayout.CENTER)
        add(deleteButton(), BorderLayout.SOUTH)
    }

    private fun deleteButton(): JButton =
        JButton("Delete Selected").apply {
            addActionListener {
                val selectedItems = list.selectedValuesList
                if (selectedItems.isNotEmpty()) {
                    SafeDeleteExecutor(project).delete(selectedItems)
                }
            }
        }


    fun setResults(results: List<AnalysisResult>) {
        model.setResults(results)
    }

    companion object {
        fun show(project: Project, results: List<AnalysisResult>) {
            val toolWindow =
                ToolWindowManager.getInstance(project).getToolWindow("Housekeeping")
                    ?: return

            val panel = toolWindow.contentManager.contents[0].component as HousekeepingPanel
            panel.setResults(results)
            toolWindow.show()
        }
    }
}

