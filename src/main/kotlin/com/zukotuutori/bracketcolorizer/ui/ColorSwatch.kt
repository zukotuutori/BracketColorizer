package com.zukotuutori.bracketcolorizer.ui

import com.intellij.ui.ColorChooserService
import com.intellij.ui.ColorPickerListener
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.zukotuutori.bracketcolorizer.settings.ColorHex
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke

/**
 * A color chip that shows its hex code in bold, in whichever of black/white is readable on
 * the color itself. Clicking (or Space/Enter) opens the IDE color picker, which is where hex
 * and RGB can be typed.
 */
class ColorSwatch(initial: Color, private val onPicked: (Color) -> Unit) : JComponent() {

    var value: Color = initial
        set(color) {
            if (field != color) {
                field = color
                toolTipText = "${ColorHex.format(color)} - click to pick a color"
                repaint()
            }
        }

    init {
        font = JBFont.label().asBold()
        preferredSize = Dimension(JBUI.scale(96), JBUI.scale(26))
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        isFocusable = true
        toolTipText = "${ColorHex.format(initial)} - click to pick a color"

        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (isEnabled) chooseColor()
            }
        })

        val pick = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (isEnabled) chooseColor()
            }
        }
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "pickColor")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "pickColor")
        actionMap.put("pickColor", pick)
    }

    private fun chooseColor() {
        requestFocusInWindow()
        val original = value
        // Follow the picker while it is open, so the row and the editor update live instead
        // of only after the dialog is confirmed.
        val livePreview = object : ColorPickerListener {
            override fun colorChanged(color: Color?) {
                if (color != null) apply(color)
            }

            override fun closed(color: Color?) = Unit
        }

        val picked = ColorChooserService.instance
            .showDialog(this, "Choose Bracket Color", original, false, listOf(livePreview))

        // Cancelled - undo whatever the live preview already applied.
        apply(picked ?: original)
    }

    private fun apply(color: Color) {
        if (color != value) {
            value = color
            onPicked(color)
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            if (!isEnabled) {
                g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f)
            }

            val arc = JBUI.scale(6)
            val w = width - 1
            val h = height - 1
            g2.color = value
            g2.fillRoundRect(0, 0, w, h, arc, arc)
            g2.color = if (hasFocus()) JBColor.namedColor("Component.focusColor", JBColor.BLUE) else BORDER
            g2.drawRoundRect(0, 0, w, h, arc, arc)

            val text = ColorHex.format(value)
            g2.font = font
            g2.color = readableOn(value)
            val metrics = g2.fontMetrics
            g2.drawString(
                text,
                (width - metrics.stringWidth(text)) / 2,
                (height - metrics.height) / 2 + metrics.ascent,
            )
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        val BORDER: JBColor = JBColor.namedColor("Component.borderColor", JBColor.GRAY)
        val DARK_TEXT: Color = Color(0x1A, 0x1A, 0x1A)

        /** Black on light colors, white on dark ones - so the hex code is always readable. */
        fun readableOn(background: Color): Color {
            val luminance = 0.299 * background.red + 0.587 * background.green + 0.114 * background.blue
            return if (luminance > 150) DARK_TEXT else Color.WHITE
        }
    }
}
