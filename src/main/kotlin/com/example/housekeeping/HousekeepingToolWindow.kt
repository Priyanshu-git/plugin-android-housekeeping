package com.example.housekeeping

import com.intellij.icons.AllIcons
import com.intellij.ide.util.DeleteHandler
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.CheckBoxList
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JPanel

class HousekeepingToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = HousekeepingToolWindowPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class HousekeepingToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private val listModel = javax.swing.DefaultListModel<JCheckBox>()
    private val list = CheckBoxList<JCheckBox>(listModel)
    private val detailsArea = JBTextArea().apply { 
        isEditable = false 
        lineWrap = true
        wrapStyleWord = true
        margin = JBUI.insets(10)
    }
    private val itemsMap = mutableMapOf<JCheckBox, UnusedItem>()

    init {
        // Toolbar
        val toolbarGroup = DefaultActionGroup()
        toolbarGroup.add(object : AnAction("Delete Selected", "Safe delete selected items", AllIcons.Actions.GC) {
            override fun actionPerformed(e: AnActionEvent) {
                deleteSelected()
            }
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = listModel.elements().toList().any { it.isSelected }
            }
        })

        // Select All
        toolbarGroup.add(object : AnAction("Select All", "Select all items", AllIcons.Actions.Selectall) {
            override fun actionPerformed(e: AnActionEvent) {
                setAllSelected(true)
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = listModel.size() > 0
            }
        })

        // Deselect All
        toolbarGroup.add(object : AnAction("Deselect All", "Deselect all items", AllIcons.Actions.Unselectall) {
            override fun actionPerformed(e: AnActionEvent) {
                setAllSelected(false)
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = listModel.size() > 0
            }
        })

        val toolbar: ActionToolbar = ActionManager.getInstance().createActionToolbar("HousekeepingToolbar", toolbarGroup, true)
        toolbar.targetComponent = this
        
        val topPanel = JPanel(BorderLayout())
        topPanel.add(toolbar.component, BorderLayout.WEST)
        
        // Splitter
        val splitter = JBSplitter(false, 0.6f)
        
        // List Side
        list.setCheckBoxListListener { index, value -> 
            // Update details on selection/click
            updateDescription(index)
        }
        
        // Handle double click to navigate
        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val index = list.locationToIndex(e.point)
                if (e.clickCount == 1)
                    updateDescription(index)
                if (e.clickCount == 2) {
                    if (index >= 0) {
                        val cb = listModel.get(index)
                        itemsMap[cb]?.element?.let { element ->
                            if (element is com.intellij.psi.PsiFile) element.navigate(true)
                            else (element as? com.intellij.psi.NavigatablePsiElement)?.navigate(true)
                        }
                    }
                }
            }
        })

        splitter.firstComponent = JBScrollPane(list)
        splitter.secondComponent = JBScrollPane(detailsArea)

        setContent(JPanel(BorderLayout()).apply {
            add(topPanel, BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
        })
    }

    private fun setAllSelected(selected: Boolean) {
        for (i in 0 until listModel.size()) {
            listModel.get(i).isSelected = selected
        }
        list.repaint()
    }


    private fun updateDescription(index: Int) {
        val cb = listModel.get(index)
        val item = itemsMap[cb]
        if (item != null) {
            detailsArea.text =
                "Name: ${item.name}\nType: ${item.type}\nPath: ${item.path}\n\nReasoning:\n${item.reason}"
        }
    }

    fun updateResults(items: List<UnusedItem>) {
        listModel.clear()
        itemsMap.clear()
        
        if (items.isEmpty()) {
            detailsArea.text = "No unused items found in the selected scope."
            return
        }

        items.forEach { item ->
            val cb = JCheckBox("${getEmoji(item.type)} ${getDisplayName(item)}")
            cb.isSelected = false // Default unchecked for safety
            itemsMap[cb] = item
            listModel.addElement(cb)
        }
        detailsArea.text = "Analysis complete. Found ${items.size} items. Select an item to view details."
    }

    private fun getDisplayName(item: UnusedItem): String {
        return when (item.type) {
            ItemType.CLASS -> item.name
            ItemType.METHOD -> item.path.substringAfterLast("/").substringBeforeLast(".") + "." + item.name
            ItemType.RESOURCE -> item.name
            ItemType.OTHER -> item.name
        }
    }

    private fun getEmoji(type: ItemType): String {
        return when (type) {
            ItemType.CLASS -> "©️"
            ItemType.METHOD -> "Ⓜ️"
            ItemType.RESOURCE -> "®️"
            else -> "❔"
        }
    }

    fun setInfoText(text: String) {
        // Appends or sets text to details area logic if desired
        // For now, we can just append to the status in the details area
        detailsArea.text = "$text\n\n${detailsArea.text}"
    }

    private fun deleteSelected() {
        val selectedElements = listModel.elements().toList()
            .filter { it.isSelected }
            .mapNotNull { itemsMap[it]?.element }
            .toTypedArray()

        if (selectedElements.isEmpty()) return

        // Use IntelliJ's built-in Safe Delete Handler
        // This handles "Search in comments", "Search in strings" dialogs automatically
        // and provides the Undo capability natively.
        DeleteHandler.deletePsiElement(selectedElements, project)
        
        // Refresh list (remove deleted items) - simplified approach
        // ideally we check if valid, but for now just clear
        val remaining = listModel.elements().toList().filter { !it.isSelected }
        listModel.clear()
        remaining.forEach { listModel.addElement(it) }
        detailsArea.text = "Deletion executed."
    }
}