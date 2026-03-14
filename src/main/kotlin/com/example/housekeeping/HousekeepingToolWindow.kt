package com.example.housekeeping

import com.intellij.icons.AllIcons
import com.intellij.ide.util.DeleteHandler
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiElement
import com.intellij.ui.CheckBoxList
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class HousekeepingToolWindowFactory : ToolWindowFactory {
    // Hidden until the first analysis run triggers toolWindow.isAvailable = true
    override fun shouldBeAvailable(project: Project) = false

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = HousekeepingToolWindowPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * A [CheckBoxList] subclass that renders IntelliJ platform icons next to each item,
 * using [adjustRendering] to insert an icon label between the checkbox and the text.
 */
private class IconCheckBoxList : CheckBoxList<UnusedItem>() {

    // Pre-allocated renderer components (reused every render call to avoid GC pressure)
    private val renderPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
    }
    private val iconLabel = JLabel().apply {
        border = JBUI.Borders.empty(0, 4)
    }
    private val textLabel = JLabel()

    override fun adjustRendering(
        rootComponent: JComponent,
        checkBox: JCheckBox,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ): JComponent {
        val item = getItemAt(index)

        // Derive display text from the item (not from checkBox.text, which we clear below)
        val displayText = if (item != null) getDisplayName(item) else checkBox.text ?: ""
        checkBox.text = ""

        iconLabel.icon = getItemIcon(item?.type)
        textLabel.text = displayText
        textLabel.foreground = checkBox.foreground
        textLabel.font = checkBox.font

        renderPanel.removeAll()
        renderPanel.background = rootComponent.background
        renderPanel.border = rootComponent.border
        renderPanel.add(checkBox)
        renderPanel.add(iconLabel)
        renderPanel.add(textLabel)

        return renderPanel
    }

    private fun getDisplayName(item: UnusedItem): String {
        return when (item.type) {
            ItemType.METHOD -> {
                val fileName = item.path.substringAfterLast("/").substringBeforeLast(".")
                "$fileName.${item.name}"
            }
            else -> item.name
        }
    }

    companion object {
        fun getItemIcon(type: ItemType?): Icon = when (type) {
            ItemType.CLASS -> AllIcons.Nodes.Class
            ItemType.METHOD -> AllIcons.Nodes.Method
            ItemType.RESOURCE -> AllIcons.Nodes.ResourceBundle
            else -> AllIcons.Nodes.Tag
        }
    }
}

class HousekeepingToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private val checkBoxList = IconCheckBoxList()
    private val detailsArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        margin = JBUI.insets(10)
    }

    init {
        // Toolbar actions
        val toolbarGroup = DefaultActionGroup()

        // Delete Selected
        toolbarGroup.add(object : AnAction("Delete Selected", "Safe delete selected items", AllIcons.Actions.GC) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun actionPerformed(e: AnActionEvent) {
                deleteSelected()
            }
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = getSelectedItems().isNotEmpty()
            }
        })

        // Select All
        toolbarGroup.add(object : AnAction("Select All", "Select all items", AllIcons.Actions.Selectall) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun actionPerformed(e: AnActionEvent) {
                setAllSelected(true)
            }
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = checkBoxList.itemsCount > 0
            }
        })

        // Deselect All
        toolbarGroup.add(object : AnAction("Deselect All", "Deselect all items", AllIcons.Actions.Unselectall) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun actionPerformed(e: AnActionEvent) {
                setAllSelected(false)
            }
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = checkBoxList.itemsCount > 0
            }
        })

        val toolbar: ActionToolbar = ActionManager.getInstance()
            .createActionToolbar("HousekeepingToolbar", toolbarGroup, true)
        toolbar.targetComponent = this

        val topPanel = JPanel(BorderLayout())
        topPanel.add(toolbar.component, BorderLayout.WEST)

        // Splitter: list + details
        val splitter = JBSplitter(false, 0.6f)

        // Checkbox click updates details
        checkBoxList.setCheckBoxListListener { index, _ ->
            updateDescription(index)
        }

        // Mouse click / double-click handlers
        checkBoxList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val index = checkBoxList.locationToIndex(e.point)
                if (e.clickCount == 1) updateDescription(index)
                if (e.clickCount == 2) navigateToItem(index)
            }
        })

        splitter.firstComponent = JBScrollPane(checkBoxList)
        splitter.secondComponent = JBScrollPane(detailsArea)

        setContent(JPanel(BorderLayout()).apply {
            add(topPanel, BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
        })

        // Initial empty state
        detailsArea.text = "Right-click a file or directory and select Housekeeping to start analysis."
    }

    // --- Public API called by actions ---

    fun showLoading(mode: AnalysisMode) {
        checkBoxList.clear()
        detailsArea.text = "Analyzing ${mode.displayName.lowercase()}...\nThis may take a moment for large scopes."
    }

    fun updateResults(items: List<UnusedItem>, mode: AnalysisMode) {
        checkBoxList.clear()

        if (items.isEmpty()) {
            detailsArea.text = "No unused ${mode.displayName.lowercase()} found in the selected scope."
            return
        }

        items.forEach { item ->
            checkBoxList.addItem(item, getDisplayName(item), false)
        }

        detailsArea.text = "Found ${items.size} unused ${mode.displayName.lowercase()}.\n" +
                "Select an item to view details.\nCheck items and click Delete to remove them."
    }

    // --- Private helpers ---

    private fun getSelectedItems(): List<UnusedItem> {
        val selected = mutableListOf<UnusedItem>()
        for (i in 0 until checkBoxList.itemsCount) {
            if (checkBoxList.isItemSelected(i)) {
                checkBoxList.getItemAt(i)?.let { selected.add(it) }
            }
        }
        return selected
    }

    private fun setAllSelected(selected: Boolean) {
        for (i in 0 until checkBoxList.itemsCount) {
            val item = checkBoxList.getItemAt(i) ?: continue
            checkBoxList.setItemSelected(item, selected)
        }
        checkBoxList.repaint()
    }

    private fun updateDescription(index: Int) {
        // Guard against -1 (click outside items) and out-of-bounds
        if (index < 0 || index >= checkBoxList.itemsCount) return
        val item = checkBoxList.getItemAt(index) ?: return
        detailsArea.text = buildString {
            append("Name: ${item.name}\n")
            append("Type: ${item.type}\n")
            append("Path: ${item.path}\n")
            append("\nReason:\n${item.reason}")
        }
    }

    private fun navigateToItem(index: Int) {
        if (index < 0 || index >= checkBoxList.itemsCount) return
        val item = checkBoxList.getItemAt(index) ?: return
        val element = item.element ?: return
        if (!element.isValid) return

        val navigated = if (element is com.intellij.psi.PsiFile) {
            element.navigate(true)
            true
        } else {
            val nav = element as? com.intellij.psi.NavigatablePsiElement
            nav?.navigate(true)
            nav != null
        }

        if (navigated) {
            detailsArea.text = detailsArea.text.trimEnd('\n') + "\n\n\u2192 Navigated to source."
        }
    }

    private fun deleteSelected() {
        // DeleteHandler.deletePsiElement() requires the EDT. Assert here so a wrong-thread
        // call fails loudly rather than silently corrupting state.
        com.intellij.openapi.application.ApplicationManager.getApplication().assertIsDispatchThread()

        val selectedItems = getSelectedItems()
        if (selectedItems.isEmpty()) return

        // Validate: element still valid AND still lives in the file it was analyzed from.
        // Guards against deleting elements that have moved or been replaced since analysis ran.
        val validItems = ReadAction.compute<List<UnusedItem>, Throwable> {
            selectedItems.filter { item ->
                val el = item.element ?: return@filter false
                if (!el.isValid) return@filter false
                val currentPath = el.containingFile?.virtualFile?.path
                currentPath != null && currentPath == item.path
            }
        }

        if (validItems.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "Selected items are no longer valid (files may have been modified).",
                "Nothing to Delete"
            )
            return
        }

        // Confirmation dialog with item preview
        val message = buildString {
            append("Are you sure you want to delete ${validItems.size} item(s)?\n\n")
            validItems.take(15).forEach { item ->
                append("  \u2022 ${item.type.name.lowercase()}: ${item.name}\n")
            }
            if (validItems.size > 15) {
                append("  ... and ${validItems.size - 15} more\n")
            }
            append("\nThis operation can be undone with Ctrl+Z.")
        }

        val result = Messages.showYesNoDialog(
            project,
            message,
            "Confirm Deletion",
            "Delete",
            "Cancel",
            Messages.getWarningIcon()
        )
        if (result != Messages.YES) return

        // Collect valid PSI elements for deletion
        val elements = ReadAction.compute<Array<PsiElement>, Throwable> {
            validItems.mapNotNull { it.element }.filter { it.isValid }.toTypedArray()
        }
        if (elements.isEmpty()) return

        // IntelliJ Safe Delete (handles conflict resolution + undo)
        DeleteHandler.deletePsiElement(elements, project)

        // Refresh list: keep only unchecked items
        val deletedCount = validItems.size
        val remaining = mutableListOf<UnusedItem>()
        for (i in 0 until checkBoxList.itemsCount) {
            if (!checkBoxList.isItemSelected(i)) {
                checkBoxList.getItemAt(i)?.let { remaining.add(it) }
            }
        }

        checkBoxList.clear()
        remaining.forEach { item ->
            checkBoxList.addItem(item, getDisplayName(item), false)
        }

        detailsArea.text = "Deleted $deletedCount item(s). Use Ctrl+Z to undo.\n" +
                "${remaining.size} item(s) remaining."
    }

    private fun getDisplayName(item: UnusedItem): String {
        return when (item.type) {
            ItemType.METHOD -> {
                val fileName = item.path.substringAfterLast("/").substringBeforeLast(".")
                "$fileName.${item.name}"
            }
            else -> item.name
        }
    }
}
