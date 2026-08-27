package com.zukotuutori.bracketcolorizer

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JPanel

/**
 * The whole configuration form. Used both by the settings dialog page
 * ([BracketColorizerConfigurable]) and by the tool window, which applies changes live.
 *
 * @param onUserChange invoked whenever the user changed something in the form
 */
class BracketColorizerSettingsPanel(private val onUserChange: () -> Unit) : JPanel(BorderLayout()) {

    private val enabledCheckBox = JBCheckBox("Enable bracket colorizing")
    private val roundCheckBox = JBCheckBox("( )")
    private val squareCheckBox = JBCheckBox("[ ]")
    private val curlyCheckBox = JBCheckBox("{ }")
    private val angleCheckBox = JBCheckBox("< >")
    private val boldCheckBox = JBCheckBox("Draw brackets in bold")
    private val cycleCheckBox = JBCheckBox("Repeat the colors when nesting deeper than the list")
    private val unmatchedCheckBox = JBCheckBox("Highlight unmatched brackets")
    private val bracketTypeCheckBoxes = listOf(roundCheckBox, squareCheckBox, curlyCheckBox, angleCheckBox)

    private val customColorsRadio = JBRadioButton("Custom colors")
    private val templateRadio = JBRadioButton("From template")
    private val templateCombo = ComboBox<BracketColorTemplate>()

    /** The templates as they are currently edited; only written back on apply. */
    private var templates: MutableList<BracketColorTemplate> = BracketColorTemplate.defaults()

    private val levelList = LevelColorList { fireChanged() }
    private val bracketTypesPanel = row {
        for (checkBox in bracketTypeCheckBoxes) add(checkBox)
    }
    private val preview = BracketPreview()
    private val legend = BracketLegend()
    private val unmatchedRow = ColorRow(JBLabel("Color"), BracketColorizerSettings.instance.unmatchedColor()) {
        fireChanged()
    }
    private val levelButtons = row {
        add(levelList.addButton)
        add(JButton("Reset to defaults").apply { addActionListener { resetToDefaults() } })
        add(JButton("Save as template...").apply { addActionListener { saveAsTemplate() } })
    }
    private val customizeButtons = row {
        add(JButton("Customize templates...").apply { addActionListener { customizeTemplates() } })
    }
    private val changeTemplateButton = JButton("Change template...").apply {
        addActionListener { changeTemplate() }
    }
    private val colorSourcePanel = row {
        add(customColorsRadio)
        add(templateRadio)
        add(templateCombo)
        add(changeTemplateButton)
    }

    private val contentPanel: JPanel
    private var suppressEvents = false

    init {
        ButtonGroup().apply {
            add(customColorsRadio)
            add(templateRadio)
        }
        for (radio in listOf(customColorsRadio, templateRadio)) {
            radio.addActionListener {
                updateColorSourceState()
                if (templateRadio.isSelected) seedFromSelectedTemplate() else fireChanged()
            }
        }
        templateCombo.addActionListener {
            if (suppressEvents) return@addActionListener
            seedFromSelectedTemplate()
        }
        templateCombo.toolTipText = "The bracket colors of a well known editor color scheme"

        for (checkBox in bracketTypeCheckBoxes) {
            checkBox.addActionListener { updateBracketTypeState() }
        }
        for (checkBox in listOf(boldCheckBox, cycleCheckBox)) {
            checkBox.addActionListener { fireChanged() }
        }
        unmatchedCheckBox.addActionListener {
            updateUnmatchedColorVisibility()
            fireChanged()
        }
        angleCheckBox.toolTipText = "Angle brackets are ambiguous (a < b) - enable only for template heavy code"
        cycleCheckBox.toolTipText =
            "When off, brackets nested deeper than the last level keep the normal text color"
        enabledCheckBox.addActionListener { updateEnabledState() }

        contentPanel = FormBuilder.createFormBuilder()
            .addComponent(TitledSeparator("Brackets"))
            .addLabeledComponent("Colorize:", bracketTypesPanel)
            .addComponent(TitledSeparator("Nesting level colors"))
            .addComponent(colorSourcePanel)
            .addComponent(levelList)
            .addComponent(levelButtons)
            .addComponent(cycleCheckBox)
            .addComponent(boldCheckBox)
            .addComponent(TitledSeparator("Unmatched brackets"))
            .addComponent(unmatchedCheckBox)
            .addComponent(unmatchedRow)
            .addComponent(TitledSeparator("Preview"))
            .addComponent(preview)
            .addComponent(legend)
            .addComponent(customizeButtons)
            .panel

        // Everything is anchored to the top; the filler in the center swallows the extra
        // height instead of the form spreading itself over the whole page.
        val top = JPanel(BorderLayout()).apply {
            add(enabledCheckBox, BorderLayout.NORTH)
            add(contentPanel, BorderLayout.CENTER)
        }
        border = JBUI.Borders.empty(10)
        add(top, BorderLayout.NORTH)
        add(JPanel(), BorderLayout.CENTER)

        writeState(BracketColorizerSettings.instance.state)
    }

    fun readState(): BracketColorizerSettings.State {
        val state = BracketColorizerSettings.State()
        state.enabled = enabledCheckBox.isSelected
        state.colorRound = roundCheckBox.isSelected
        state.colorSquare = squareCheckBox.isSelected
        state.colorCurly = curlyCheckBox.isSelected
        state.colorAngle = angleCheckBox.isSelected
        state.boldBrackets = boldCheckBox.isSelected
        state.cycleColors = cycleCheckBox.isSelected
        state.highlightUnmatched = unmatchedCheckBox.isSelected
        state.unmatchedColor = ColorHex.format(unmatchedRow.color)
        state.levelColors = ArrayList(levelList.colors)
        state.templates = templates.mapTo(ArrayList()) { it.copy() }
        state.useTemplate = templateRadio.isSelected && templates.isNotEmpty()
        (templateCombo.selectedItem as? BracketColorTemplate)?.let { state.templateId = it.id }
        return state
    }

    fun writeState(state: BracketColorizerSettings.State) {
        suppressEvents = true
        try {
            enabledCheckBox.isSelected = state.enabled
            roundCheckBox.isSelected = state.colorRound
            squareCheckBox.isSelected = state.colorSquare
            curlyCheckBox.isSelected = state.colorCurly
            angleCheckBox.isSelected = state.colorAngle
            boldCheckBox.isSelected = state.boldBrackets
            cycleCheckBox.isSelected = state.cycleColors
            unmatchedCheckBox.isSelected = state.highlightUnmatched
            unmatchedRow.color = ColorHex.parse(state.unmatchedColor)
                ?: ColorHex.parse(BracketColorizerSettings.DEFAULT_UNMATCHED_COLOR)!!
            levelList.colors = state.levelColors
            templates = state.templates.mapTo(ArrayList()) { it.copy() }
            rebuildTemplateCombo(state.templateId)
            customColorsRadio.isSelected = !state.useTemplate
            templateRadio.isSelected = state.useTemplate
            updateColorSourceState()
            updateBracketTypeState()
        } finally {
            suppressEvents = false
        }
        updatePreview()
    }

    /**
     * Takes the bracket settings of the selected template into the form. They stay editable
     * from here on - a template seeds them, it does not own them.
     */
    private fun seedFromSelectedTemplate() {
        val template = templateCombo.selectedItem as? BracketColorTemplate ?: return
        val previous = suppressEvents
        suppressEvents = true
        try {
            roundCheckBox.isSelected = template.colorRound
            squareCheckBox.isSelected = template.colorSquare
            curlyCheckBox.isSelected = template.colorCurly
            angleCheckBox.isSelected = template.colorAngle
            cycleCheckBox.isSelected = template.cycleColors
            boldCheckBox.isSelected = template.boldBrackets
            unmatchedCheckBox.isSelected = template.highlightUnmatched
        } finally {
            suppressEvents = previous
        }
        // Re-runs the master switch rules and repaints the preview.
        updateBracketTypeState()
    }

    private fun rebuildTemplateCombo(selectedId: String) {
        val previous = suppressEvents
        suppressEvents = true
        try {
            templateCombo.model = DefaultComboBoxModel(templates.toTypedArray())
            templateCombo.selectedItem = templates.firstOrNull { it.id == selectedId } ?: templates.firstOrNull()
        } finally {
            suppressEvents = previous
        }
    }

    /** Turns the colors that are currently picked by hand into a named template. */
    private fun saveAsTemplate() {
        val name = Messages.showInputDialog(
            this,
            "Name for the current colors:",
            "Save as Template",
            null,
            "My colors ${templates.count { !it.builtIn } + 1}",
            object : InputValidator {
                override fun checkInput(input: String?): Boolean {
                    val trimmed = input?.trim().orEmpty()
                    return trimmed.isNotEmpty() &&
                        templates.none { it.displayName.equals(trimmed, ignoreCase = true) }
                }

                override fun canClose(input: String?): Boolean = checkInput(input)
            },
        )?.trim() ?: return

        val saved = BracketColorTemplate(
            freshTemplateId(),
            name,
            levelList.colors,
            ColorHex.format(unmatchedRow.color),
        ).also {
            it.colorRound = roundCheckBox.isSelected
            it.colorSquare = squareCheckBox.isSelected
            it.colorCurly = curlyCheckBox.isSelected
            it.colorAngle = angleCheckBox.isSelected
            it.cycleColors = cycleCheckBox.isSelected
            it.boldBrackets = boldCheckBox.isSelected
            it.highlightUnmatched = unmatchedCheckBox.isSelected
        }
        templates.add(saved)
        // The new template holds exactly the colors that are on screen, so switching to it
        // changes nothing but shows the user where their palette ended up.
        templateRadio.isSelected = true
        rebuildTemplateCombo(saved.id)
        updateColorSourceState()
        fireChanged()
    }

    private fun freshTemplateId(): String {
        var index = 1
        while (templates.any { it.id == "custom-$index" }) index++
        return "custom-$index"
    }

    /** Opens the selected template for editing; the result replaces it in place. */
    private fun changeTemplate() {
        val selected = templateCombo.selectedItem as? BracketColorTemplate ?: return
        val dialog = TemplateEditorDialog(
            this,
            selected,
            templates.filter { it.id != selected.id }.map { it.displayName },
        )
        if (!dialog.showAndGet()) return

        val edited = dialog.result
        templates[templates.indexOfFirst { it.id == edited.id }] = edited
        rebuildTemplateCombo(edited.id)
        updateColorSourceState()
        if (templateRadio.isSelected) seedFromSelectedTemplate() else fireChanged()
    }

    private fun customizeTemplates() {
        val dialog = TemplateManagerDialog(this, templates)
        if (!dialog.showAndGet()) return

        val selectedId = (templateCombo.selectedItem as? BracketColorTemplate)?.id.orEmpty()
        templates = dialog.templates.mapTo(ArrayList()) { it.copy() }
        rebuildTemplateCombo(selectedId)
        if (templates.isEmpty()) customColorsRadio.isSelected = true
        updateColorSourceState()
        if (templateRadio.isSelected) seedFromSelectedTemplate() else fireChanged()
    }

    private fun resetToDefaults() {
        unmatchedRow.color = ColorHex.parse(BracketColorizerSettings.DEFAULT_UNMATCHED_COLOR)!!
        levelList.colors = BracketColorizerSettings.DEFAULT_LEVEL_COLORS
    }

    private fun updateEnabledState() {
        UIUtil.setEnabled(contentPanel, enabledCheckBox.isSelected, true)
        // A form full of colors that do nothing is confusing, so the switch says so in red.
        enabledCheckBox.foreground =
            if (enabledCheckBox.isSelected) UIUtil.getLabelForeground() else DISABLED_FOREGROUND
        // Bracket types must remain selectable while the master switch is off; otherwise
        // clearing the last type would leave no way to make the master switch available again.
        UIUtil.setEnabled(bracketTypesPanel, true, true)
        updateColorSourceState()
        fireChanged()
    }

    private fun updateBracketTypeState() {
        val hasSelectedType = bracketTypeCheckBoxes.any { it.isSelected }
        // Picking a bracket type while there was none turns the colorizing back on: it was
        // only switched off because there was nothing left to color. A state that is written
        // into the form keeps its own value, hence the check for a user driven change.
        if (!suppressEvents && hasSelectedType && !enabledCheckBox.isEnabled) {
            enabledCheckBox.isSelected = true
        }
        enabledCheckBox.isEnabled = hasSelectedType
        if (!hasSelectedType) enabledCheckBox.isSelected = false
        updateEnabledState()
    }

    /** The hand picked colors are only editable while the template mode is off. */
    private fun updateColorSourceState() {
        // Without a single template left there is nothing to switch to.
        if (templates.isEmpty()) customColorsRadio.isSelected = true
        templateRadio.isEnabled = templates.isNotEmpty()

        val custom = customColorsRadio.isSelected
        levelList.isVisible = custom
        levelButtons.isVisible = custom
        templateCombo.isVisible = !custom
        changeTemplateButton.isVisible = !custom
        updateUnmatchedColorVisibility()
    }

    private fun updateUnmatchedColorVisibility() {
        unmatchedRow.isVisible = unmatchedCheckBox.isSelected && customColorsRadio.isSelected
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun fireChanged() {
        if (suppressEvents) return
        updatePreview()
        onUserChange()
    }

    private fun updatePreview() {
        val state = readState()
        preview.setState(state)
        legend.setState(state)
    }

    private companion object {
        val DISABLED_FOREGROUND: JBColor = JBColor.namedColor("Label.errorForeground", JBColor.RED)
    }

    private fun row(fill: JPanel.() -> Unit): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
            isOpaque = false
            fill()
        }
}
