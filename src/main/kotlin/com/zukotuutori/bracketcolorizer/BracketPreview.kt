package com.zukotuutori.bracketcolorizer

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent

/**
 * A staircase of nested braces, one level per step, drawn with the editor's own font,
 * background and text color - so the level colors can be judged exactly as they will
 * look in the editor.
 */
class BracketPreview : JComponent() {

    private var state: BracketColorizerSettings.State = BracketColorizerSettings.State()
    private var lines: List<String> = emptyList()
    private var index: BracketIndex.Index = BracketIndex.ofPlainText("")

    init {
        isOpaque = false
        toolTipText = "How the current colors look in the editor"
        setState(BracketColorizerSettings.State())
    }

    fun setState(newState: BracketColorizerSettings.State) {
        state = newState.copy()
        lines = buildStaircase()
        index = BracketIndex.ofPlainText(lines.joinToString("\n"))
        revalidate()
        repaint()
    }

    /**
     * One step in and one step out per nesting level. One level more than there are colors
     * (when they cycle), so that the wrap-around back to the first color is visible.
     */
    private fun buildStaircase(): List<String> {
        val depth = depthFor(state)

        val lines = ArrayList<String>(2 * depth)
        for (level in 0 until depth) lines.add(INDENT.repeat(level) + "{")
        for (level in depth - 1 downTo 0) lines.add(INDENT.repeat(level) + "}")
        return lines
    }

    override fun getPreferredSize(): Dimension {
        val metrics = getFontMetrics(codeFont())
        val columns = lines.maxOf { it.length }
        return Dimension(
            columns * metrics.charWidth('m') + 2 * PADDING,
            lines.size * metrics.height + 2 * PADDING,
        )
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val plain = codeFont()
        val bold = plain.deriveFont(Font.BOLD)
        val brackets = if (state.enabled) activeBracketChars() else ""

        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val arc = JBUI.scale(6)
            g2.color = scheme.defaultBackground
            g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
            g2.color = BORDER
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)

            val metrics = g2.getFontMetrics(plain)
            val charWidth = metrics.charWidth('m')
            val lineHeight = metrics.height
            val defaultText = scheme.defaultForeground

            var offset = 0
            var y = PADDING + metrics.ascent
            for (line in lines) {
                for ((column, char) in line.withIndex()) {
                    val level = if (brackets.indexOf(char) >= 0) index.levelAt(offset + column, char) else null
                    val color = when {
                        level == null -> defaultText
                        level >= 0 -> colorForLevel(level) ?: defaultText
                        state.highlightUnmatched -> ColorHex.parse(state.unmatchedColor) ?: defaultText
                        else -> defaultText
                    }
                    g2.color = color
                    g2.font = if (level != null && state.boldBrackets) bold else plain
                    g2.drawString(char.toString(), PADDING + column * charWidth, y)
                }
                offset += line.length + 1
                y += lineHeight
            }
        } finally {
            g2.dispose()
        }
    }

    /** The staircase is made of braces only, so only that setting matters here. */
    private fun activeBracketChars(): String = if (state.colorCurly) "{}" else ""

    private fun colorForLevel(level: Int): Color? = state.colorAt(level)

    private fun codeFont(): Font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)

    companion object {
        private val PADDING: Int = JBUI.scale(8)
        private val BORDER: JBColor = JBColor.namedColor("Component.borderColor", JBColor.GRAY)
        private const val INDENT = "  "
        private const val MAX_DEPTH = 12

        /**
         * How many levels the preview shows: all configured colors plus one more, so that what
         * happens below the last level is visible - the wrap-around back to the first color,
         * or no color at all.
         */
        fun depthFor(state: BracketColorizerSettings.State): Int =
            (state.levelColors.size + 1).coerceIn(1, MAX_DEPTH)
    }
}
