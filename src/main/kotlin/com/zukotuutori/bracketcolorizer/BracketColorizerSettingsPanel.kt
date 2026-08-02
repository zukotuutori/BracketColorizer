package com.zukotuutori.bracketcolorizer

import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BoxLayout
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

    private val levelRows = mutableListOf<ColorRow>()
    private val levelsPanel = JPanel()
    private val bracketTypesPanel = row {
        for (checkBox in bracketTypeCheckBoxes) add(checkBox)
    }
    private val preview = BracketPreview()
    private val legend = BracketLegend()
    private val unmatchedRow = ColorRow(JBLabel("Color"), BracketColorizerSettings.instance.unmatchedColor()) {
        fireChanged()
    }
    private val addLevelButton = JButton("Add level").apply { addActionListener { addLevel() } }

    private val contentPanel: JPanel
    private var suppressEvents = false

    init {
        levelsPanel.layout = BoxLayout(levelsPanel, BoxLayout.Y_AXIS)
        levelsPanel.alignmentX = LEFT_ALIGNMENT

        val levelButtons = row {
            add(addLevelButton)
            add(JButton("Reset to defaults").apply { addActionListener { resetToDefaults() } })
        }

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
            .addComponent(levelsPanel)
            .addComponent(levelButtons)
            .addComponent(cycleCheckBox)
            .addComponent(boldCheckBox)
            .addComponent(TitledSeparator("Unmatched brackets"))
            .addComponent(unmatchedCheckBox)
            .addComponent(unmatchedRow)
            .addComponent(TitledSeparator("Preview"))
            .addComponent(preview)
            .addComponent(legend)
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
        state.levelColors = levelRows.mapTo(ArrayList()) { ColorHex.format(it.color) }
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
            rebuildLevels(state.levelColors)
            updateUnmatchedColorVisibility()
            updateBracketTypeState()
        } finally {
            suppressEvents = false
        }
        updatePreview()
    }

    private fun rebuildLevels(colors: List<String>) {
        levelRows.clear()
        levelsPanel.removeAll()
        val effective = (if (colors.isEmpty()) BracketColorizerSettings.DEFAULT_LEVEL_COLORS else colors)
            .take(BracketColorizerSettings.MAX_LEVEL_COLORS)
        for ((index, hex) in effective.withIndex()) {
            val color = ColorHex.parse(hex) ?: ColorHex.parse(BracketColorizerSettings.DEFAULT_LEVEL_COLORS[0])!!
            val row = ColorRow(JBLabel("Level ${index + 1}"), color) { fireChanged() }
            levelRows.add(row)
            levelsPanel.add(row)
        }
        // Keep at least one level around, otherwise there would be nothing to configure.
        val removeHandler: ((ColorRow) -> Unit)? = if (levelRows.size > 1) ::removeLevel else null
        for (row in levelRows) row.onRemove = removeHandler
        addLevelButton.isEnabled = levelRows.size < BracketColorizerSettings.MAX_LEVEL_COLORS

        levelsPanel.revalidate()
        levelsPanel.repaint()
        fireChanged()
    }

    private fun addLevel() {
        if (levelRows.size >= BracketColorizerSettings.MAX_LEVEL_COLORS) return
        val next = BracketColorizerSettings.DEFAULT_LEVEL_COLORS[
            levelRows.size % BracketColorizerSettings.DEFAULT_LEVEL_COLORS.size
        ]
        rebuildLevels(levelRows.map { ColorHex.format(it.color) } + next)
    }

    private fun removeLevel(row: ColorRow) {
        if (levelRows.size <= 1) return
        rebuildLevels(levelRows.filter { it !== row }.map { ColorHex.format(it.color) })
    }

    private fun resetToDefaults() {
        unmatchedRow.color = ColorHex.parse(BracketColorizerSettings.DEFAULT_UNMATCHED_COLOR)!!
        rebuildLevels(BracketColorizerSettings.DEFAULT_LEVEL_COLORS)
    }

    private fun updateEnabledState() {
        UIUtil.setEnabled(contentPanel, enabledCheckBox.isSelected, true)
        // Bracket types must remain selectable while the master switch is off; otherwise
        // clearing the last type would leave no way to make the master switch available again.
        UIUtil.setEnabled(bracketTypesPanel, true, true)
        fireChanged()
    }

    private fun updateBracketTypeState() {
        val hasSelectedType = bracketTypeCheckBoxes.any { it.isSelected }
        enabledCheckBox.isEnabled = hasSelectedType
        if (!hasSelectedType) enabledCheckBox.isSelected = false
        updateEnabledState()
    }

    private fun updateUnmatchedColorVisibility() {
        unmatchedRow.isVisible = unmatchedCheckBox.isSelected
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

    private fun row(fill: JPanel.() -> Unit): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
            isOpaque = false
            fill()
        }
}
