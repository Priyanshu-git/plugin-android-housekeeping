package com.example.housekeeping.ui

import com.example.housekeeping.analysis.AnalysisResult
import com.intellij.icons.AllIcons
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class ResultsRenderer : JPanel(), ListCellRenderer<AnalysisResult> {

    private val checkbox = JCheckBox()
    private val label = JLabel()

    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        checkbox.isEnabled = false // visual only
        add(checkbox)
        add(Box.createHorizontalStrut(6))
        add(label)
        isOpaque = true
    }

    override fun getListCellRendererComponent(
        list: JList<out AnalysisResult>,
        value: AnalysisResult,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {

        checkbox.isSelected = isSelected

        when (value) {
            is AnalysisResult.MethodResult -> {
                label.icon = AllIcons.Nodes.Method
                label.text = value.method.name
            }
            is AnalysisResult.ClassResult -> {
                label.icon = AllIcons.Nodes.Class
                label.text = value.clazz.qualifiedName ?: value.clazz.name ?: "<anonymous class>"
            }
            is AnalysisResult.ResourceResult -> {
                label.icon = AllIcons.FileTypes.Xml
                label.text = value.file.path
            }
        }

        background = if (isSelected) list.selectionBackground else list.background
        foreground = if (isSelected) list.selectionForeground else list.foreground

        return this
    }

}
