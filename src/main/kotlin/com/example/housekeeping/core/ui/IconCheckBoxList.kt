package com.example.housekeeping.core.ui

import com.example.housekeeping.core.model.ItemType
import com.example.housekeeping.core.model.UnusedItem
import com.intellij.icons.AllIcons
import com.intellij.ui.CheckBoxList
import com.intellij.util.ui.JBUI
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class IconCheckBoxList : CheckBoxList<UnusedItem>() {

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

    companion object {
        fun getItemIcon(type: ItemType?): Icon = when (type) {
            ItemType.CLASS -> AllIcons.Nodes.Class
            ItemType.METHOD -> AllIcons.Nodes.Method
            ItemType.RESOURCE -> AllIcons.Nodes.ResourceBundle
            else -> AllIcons.Nodes.Tag
        }

        fun getDisplayName(item: UnusedItem): String {
            return when (item.type) {
                ItemType.METHOD -> {
                    val fileName = item.path.substringAfterLast("/").substringBeforeLast(".")
                    "$fileName.${item.name}"
                }
                else -> item.name
            }
        }
    }
}
