package com.zukotuutori.bracketcolorizer.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.zukotuutori.bracketcolorizer.settings.BracketColorTemplate
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * "Customize templates": shows every template there is and lets the list be emptied or the
 * built in templates put back. The result is only handed over when the dialog is confirmed,
 * see [templates].
 */
class TemplateManagerDialog(
    parent: Component,
    current: List<BracketColorTemplate>,
) : DialogWrapper(parent, false) {

    private val model = DefaultListModel<BracketColorTemplate>().apply {
        for (template in current) addElement(template.copy())
    }
    private val list = JBList(model)
    private val changeButton = JButton("Change template...")
    private val removeButton = JButton("Remove selected")
    private val deleteAllButton = JButton("Delete all")
    private val restoreButton = JButton("Restore defaults")

    /** Built once and kept, so that a test can drive the buttons without a dialog window. */
    internal val centerPanel: JPanel by lazy { buildCenterPanel() }

    /** The edited list, valid after the dialog was closed with OK. */
    val templates: List<BracketColorTemplate>
        get() = (0 until model.size()).map { model.getElementAt(it) }

    init {
        title = "Customize Templates"
        init()
    }

    override fun createCenterPanel(): JComponent = centerPanel

    private fun buildCenterPanel(): JPanel {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.visibleRowCount = 10
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                selected: Boolean,
                focused: Boolean,
            ): Component {
                val template = value as BracketColorTemplate
                val label = super.getListCellRendererComponent(list, template.displayName, index, selected, focused)
                toolTipText = template.levelColors.joinToString(" ")
                return label
            }
        }
        list.addListSelectionListener { updateButtons() }

        changeButton.addActionListener { changeSelected() }
        removeButton.addActionListener {
            val index = list.selectedIndex
            if (index >= 0) {
                model.remove(index)
                updateButtons()
            }
        }
        deleteAllButton.addActionListener {
            model.clear()
            updateButtons()
        }
        restoreButton.addActionListener {
            // Built in templates go back to how they shipped, saved ones stay untouched.
            val restored = BracketColorTemplate.withDefaultsRestored(templates)
            model.clear()
            for (template in restored) model.addElement(template)
            updateButtons()
        }

        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
            isOpaque = false
            add(changeButton)
            add(removeButton)
            add(deleteAllButton)
            add(restoreButton)
        }
        val hint = JBLabel("Restoring the defaults undoes changes to the built in templates and keeps your own.").apply {
            foreground = UIUtil.getContextHelpForeground()
            border = JBUI.Borders.emptyTop(8)
        }

        updateButtons()
        return JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            preferredSize = Dimension(JBUI.scale(380), JBUI.scale(300))
            add(JBScrollPane(list), BorderLayout.CENTER)
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                add(buttons, BorderLayout.NORTH)
                add(hint, BorderLayout.SOUTH)
            }, BorderLayout.SOUTH)
        }
    }

    /** Edits the selected entry with the same dialog the settings form uses. */
    private fun changeSelected() {
        val index = list.selectedIndex
        if (index < 0) return
        val current = model.getElementAt(index)
        val editor = TemplateEditorDialog(
            list,
            current,
            templates.filter { it.id != current.id }.map { it.displayName },
        )
        if (!editor.showAndGet()) return

        model.setElementAt(editor.result, index)
        list.selectedIndex = index
        updateButtons()
    }

    private fun updateButtons() {
        changeButton.isEnabled = list.selectedIndex >= 0
        removeButton.isEnabled = list.selectedIndex >= 0
        deleteAllButton.isEnabled = !model.isEmpty
        val current = templates
        restoreButton.isEnabled =
            BracketColorTemplate.withDefaultsRestored(current).map { it.signature() } !=
                current.map { it.signature() }
    }
}
