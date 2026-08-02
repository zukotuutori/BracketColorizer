package com.zukotuutori.bracketcolorizer

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import java.awt.Font

/**
 * Paints every bracket with the color configured for its nesting level.
 *
 * Registered for all languages; the actual nesting information comes from [BracketIndex],
 * which is computed once per file and cached until the file changes.
 */
class BracketColorizerAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Only leaves, so that every character is visited exactly once.
        if (element.firstChild != null) return

        val settings = BracketColorizerSettings.instance
        if (!settings.state.enabled) return

        val brackets = settings.activeBracketChars()
        if (brackets.isEmpty()) return

        val text = element.text
        if (text.isEmpty() || text.none { brackets.indexOf(it) >= 0 }) return

        val file = element.containingFile ?: return
        val index = BracketIndex.of(file)
        if (index.isEmpty) return

        val start = element.textRange.startOffset
        val fontType = if (settings.state.boldBrackets) Font.BOLD else Font.PLAIN

        for (i in text.indices) {
            val char = text[i]
            if (brackets.indexOf(char) < 0) continue

            val level = index.levelAt(start + i, char) ?: continue
            val color = when {
                // Nesting deeper than the configured levels keeps the normal text color.
                level >= 0 -> settings.colorForLevel(level) ?: continue
                settings.state.highlightUnmatched -> settings.unmatchedColor()
                else -> continue
            }

            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(start + i, start + i + 1))
                .enforcedTextAttributes(TextAttributes(color, null, null, null, fontType))
                .create()
        }
    }
}
