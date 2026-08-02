package com.zukotuutori.bracketcolorizer

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * The lines below the staircase preview: one per bracket type, each showing every nesting
 * level in its color, plus the unmatched color.
 */
class BracketLegend : JPanel() {

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.emptyLeft(14)
        alignmentX = LEFT_ALIGNMENT
    }

    fun setState(state: BracketColorizerSettings.State) {
        removeAll()

        addRow("Round brackets:", nestedHtml(state, '(', ')', state.colorRound))
        addRow("Square brackets:", nestedHtml(state, '[', ']', state.colorSquare))
        addRow("Angle brackets:", nestedHtml(state, '<', '>', state.colorAngle))
        if (state.highlightUnmatched) {
            val color = if (state.enabled) state.unmatchedColor else null
            addRow("Unmatched bracket:", html(state) { append(glyph('}', color)) })
        }

        revalidate()
        repaint()
    }

    /** All levels of one bracket type on a single line: `((( ... )))`, each pair in its color. */
    private fun nestedHtml(
        state: BracketColorizerSettings.State,
        open: Char,
        close: Char,
        colorized: Boolean,
    ): String {
        val depth = BracketPreview.depthFor(state)
        val active = colorized && state.enabled
        fun hexAt(level: Int): String? =
            if (active) state.colorAt(level)?.let { ColorHex.format(it) } else null

        return html(state) {
            for (level in 0 until depth) append(glyph(open, hexAt(level)))
            append("&nbsp;")
            for (level in depth - 1 downTo 0) append(glyph(close, hexAt(level)))
        }
    }

    private fun html(state: BracketColorizerSettings.State, body: StringBuilder.() -> Unit): String =
        buildString {
            append("<html>")
            if (state.boldBrackets && state.enabled) append("<b>")
            body()
            if (state.boldBrackets && state.enabled) append("</b>")
            append("</html>")
        }

    /** `null` color means "not colorized" - the label's own foreground is used. */
    private fun glyph(char: Char, hex: String?): String {
        val text = if (char == '<') "&lt;" else if (char == '>') "&gt;" else char.toString()
        return if (hex == null) text else "<font color='$hex'>$text</font>"
    }

    private fun addRow(caption: String, bracketsHtml: String) {
        val captionLabel = JBLabel(caption).apply {
            foreground = UIUtil.getContextHelpForeground()
            preferredSize = Dimension(
                maxOf(JBUI.scale(130), preferredSize.width),
                preferredSize.height,
            )
        }
        val bracketsLabel = JBLabel(bracketsHtml).apply {
            font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
        }

        val row = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(1))).apply {
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            add(captionLabel)
            add(bracketsLabel)
        }
        row.maximumSize = Dimension(Int.MAX_VALUE, row.preferredSize.height)
        add(row)
    }
}
