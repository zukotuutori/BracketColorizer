package com.zukotuutori.bracketcolorizer.ui

import com.intellij.icons.AllIcons
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * One editable color: a label and a [ColorSwatch] that shows the hex code and opens the IDE
 * color picker (which offers hex and RGB input).
 */
class ColorRow(
    leading: JComponent,
    initial: Color,
    private val onChanged: () -> Unit,
) : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))) {

    private val swatch = ColorSwatch(initial) { onChanged() }
    private val removeButton = JButton(AllIcons.General.Remove)

    /** Invoked when the user clicks the remove button; the button is hidden while `null`. */
    var onRemove: ((ColorRow) -> Unit)? = null
        set(value) {
            field = value
            removeButton.isVisible = value != null
        }

    var color: Color
        get() = swatch.value
        set(value) {
            swatch.value = value
        }

    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT

        removeButton.isVisible = false
        removeButton.toolTipText = "Remove this nesting level"
        removeButton.margin = JBUI.insets(2)
        removeButton.preferredSize = Dimension(JBUI.scale(34), JBUI.scale(26))
        removeButton.addActionListener { onRemove?.invoke(this) }

        // Fixed minimum width so the swatches of all rows line up.
        leading.preferredSize = Dimension(
            maxOf(JBUI.scale(56), leading.preferredSize.width),
            leading.preferredSize.height,
        )
        add(leading)
        add(swatch)
        add(removeButton)
    }

    /** Keep the row at its natural height when stacked in a vertical BoxLayout. */
    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}
