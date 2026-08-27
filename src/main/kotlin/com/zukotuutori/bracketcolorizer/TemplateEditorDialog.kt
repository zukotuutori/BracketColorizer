package com.zukotuutori.bracketcolorizer

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Edits one template: its name, its nesting colors and its unmatched color. The changes are
 * only handed back when the dialog is saved, see [result].
 *
 * @param takenNames the names of the other templates, which this one may not collide with
 */
class TemplateEditorDialog(
    parent: Component,
    private val original: BracketColorTemplate,
    private val takenNames: List<String>,
) : DialogWrapper(parent, false) {

    private val nameField = JBTextField(original.displayName, 24)
    private val levelList = LevelColorList { }
    private val unmatchedRow = ColorRow(
        JBLabel("Unmatched"),
        ColorHex.parse(original.unmatchedColor) ?: ColorHex.parse(BracketColorizerSettings.DEFAULT_UNMATCHED_COLOR)!!,
    ) { }

    private val roundCheckBox = JBCheckBox("( )", original.colorRound)
    private val squareCheckBox = JBCheckBox("[ ]", original.colorSquare)
    private val curlyCheckBox = JBCheckBox("{ }", original.colorCurly)
    private val angleCheckBox = JBCheckBox("< >", original.colorAngle)
    private val cycleCheckBox =
        JBCheckBox("Repeat the colors when nesting deeper than the list", original.cycleColors)
    private val boldCheckBox = JBCheckBox("Draw brackets in bold", original.boldBrackets)
    private val unmatchedCheckBox =
        JBCheckBox("Highlight unmatched brackets", original.highlightUnmatched)

    internal val centerPanel: JPanel by lazy { buildCenterPanel() }

    /** The edited template, valid after the dialog was saved. Keeps the identity of the original. */
    val result: BracketColorTemplate
        get() = BracketColorTemplate(
            original.id,
            nameField.text.trim(),
            levelList.colors,
            ColorHex.format(unmatchedRow.color),
            original.builtIn,
        ).also {
            it.colorRound = roundCheckBox.isSelected
            it.colorSquare = squareCheckBox.isSelected
            it.colorCurly = curlyCheckBox.isSelected
            it.colorAngle = angleCheckBox.isSelected
            it.cycleColors = cycleCheckBox.isSelected
            it.boldBrackets = boldCheckBox.isSelected
            it.highlightUnmatched = unmatchedCheckBox.isSelected
        }

    init {
        title = "Change Template"
        setOKButtonText("Save")
        setCancelButtonText("Discard")
        init()
    }

    override fun createCenterPanel(): JComponent = centerPanel

    override fun getPreferredFocusedComponent(): JComponent = nameField

    public override fun doValidate(): ValidationInfo? {
        val name = nameField.text.trim()
        return when {
            name.isEmpty() -> ValidationInfo("The template needs a name", nameField)
            takenNames.any { it.equals(name, ignoreCase = true) } ->
                ValidationInfo("A template with this name already exists", nameField)
            else -> null
        }
    }

    private fun row(vararg components: JComponent): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
            isOpaque = false
            for (component in components) add(component)
        }

    private fun buildCenterPanel(): JPanel {
        levelList.colors = original.levelColors
        angleCheckBox.toolTipText = "Angle brackets are ambiguous (a < b) - enable only for template heavy code"
        unmatchedCheckBox.addActionListener { unmatchedRow.isVisible = unmatchedCheckBox.isSelected }
        unmatchedRow.isVisible = unmatchedCheckBox.isSelected

        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent("Name:", nameField)
            .addComponent(TitledSeparator("Brackets"))
            .addLabeledComponent("Colorize:", row(roundCheckBox, squareCheckBox, curlyCheckBox, angleCheckBox))
            .addComponent(TitledSeparator("Nesting level colors"))
            .addComponent(levelList)
            .addComponent(row(levelList.addButton))
            .addComponent(cycleCheckBox)
            .addComponent(boldCheckBox)
            .addComponent(TitledSeparator("Unmatched brackets"))
            .addComponent(unmatchedCheckBox)
            .addComponent(unmatchedRow)
            .panel

        return JPanel(BorderLayout()).apply {
            preferredSize = Dimension(JBUI.scale(420), JBUI.scale(440))
            add(JBScrollPane(form).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
        }
    }
}
