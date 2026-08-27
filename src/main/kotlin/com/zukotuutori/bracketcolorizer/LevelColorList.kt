package com.zukotuutori.bracketcolorizer

import com.intellij.ui.components.JBLabel
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

/**
 * The stack of "Level N" color rows, with its own add button and a remove button per row.
 * Used by the settings form for the hand picked colors and by [TemplateEditorDialog].
 *
 * @param onChanged invoked whenever a color, or the number of levels, changed
 */
class LevelColorList(private val onChanged: () -> Unit) : JPanel() {

    private val rows = mutableListOf<ColorRow>()

    /** Belongs next to the other buttons of the surrounding form, hence not added here. */
    val addButton: JButton = JButton("Add level").apply { addActionListener { addLevel() } }

    var colors: List<String>
        get() = rows.map { ColorHex.format(it.color) }
        set(value) = rebuild(value)

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = LEFT_ALIGNMENT
        isOpaque = false
    }

    private fun rebuild(colors: List<String>) {
        rows.clear()
        removeAll()
        val effective = (if (colors.isEmpty()) BracketColorizerSettings.DEFAULT_LEVEL_COLORS else colors)
            .take(BracketColorizerSettings.MAX_LEVEL_COLORS)
        for ((index, hex) in effective.withIndex()) {
            val color = ColorHex.parse(hex) ?: ColorHex.parse(BracketColorizerSettings.DEFAULT_LEVEL_COLORS[0])!!
            val row = ColorRow(JBLabel("Level ${index + 1}"), color) { onChanged() }
            rows.add(row)
            add(row)
        }
        // Keep at least one level around, otherwise there would be nothing to configure.
        val removeHandler: ((ColorRow) -> Unit)? = if (rows.size > 1) ::removeLevel else null
        for (row in rows) row.onRemove = removeHandler
        addButton.isEnabled = rows.size < BracketColorizerSettings.MAX_LEVEL_COLORS

        revalidate()
        repaint()
        onChanged()
    }

    private fun addLevel() {
        if (rows.size >= BracketColorizerSettings.MAX_LEVEL_COLORS) return
        val next = BracketColorizerSettings.DEFAULT_LEVEL_COLORS[
            rows.size % BracketColorizerSettings.DEFAULT_LEVEL_COLORS.size
        ]
        rebuild(colors + next)
    }

    private fun removeLevel(row: ColorRow) {
        if (rows.size <= 1) return
        rebuild(rows.filter { it !== row }.map { ColorHex.format(it.color) })
    }
}
