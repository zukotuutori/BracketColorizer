package com.zukotuutori.bracketcolorizer

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/**
 * Computes, for every bracket in a file, the nesting level it belongs to.
 *
 * The file is tokenized with the language's highlighting lexer so that brackets inside
 * strings and comments are ignored. The result is cached per [PsiFile] and dropped
 * whenever the file is modified.
 */
object BracketIndex {

    /** Level value used for brackets that have no counterpart. */
    const val UNMATCHED: Int = -1

    private const val MAX_FILE_LENGTH = 2_000_000
    private const val MAX_BRACKETS_PER_GROUP = 200_000
    private const val CANCELLATION_CHECK_INTERVAL = 4_096

    private const val OPENERS = "([{"
    private const val CLOSERS = ")]}"

    private val KEY = Key.create<CachedValue<Index>>("bracket.colorizer.index")

    /**
     * Prose, not code. Brackets carry no nesting structure here, and Markdown link syntax
     * like `[text](url)` would light up on every line, so these files are left alone.
     * Code fenced into a Markdown file is a different language and is still colorized.
     */
    private val EXCLUDED_LANGUAGE_IDS = setOf("TEXT", "Markdown")

    /**
     * @param main   offset -> nesting level for `(`, `[`, `{` and their closers
     * @param angle  offset -> nesting level for `<` and `>`, tracked on a separate stack
     *               so that comparisons like `a < b` cannot shift the levels of real brackets
     */
    class Index(@JvmField val main: Map<Int, Int>, @JvmField val angle: Map<Int, Int>) {
        val isEmpty: Boolean get() = main.isEmpty() && angle.isEmpty()

        fun levelAt(offset: Int, char: Char): Int? =
            if (char == '<' || char == '>') angle[offset] else main[offset]
    }

    private val EMPTY = Index(emptyMap(), emptyMap())

    fun of(file: PsiFile): Index =
        CachedValuesManager.getCachedValue(file, KEY) {
            CachedValueProvider.Result.create(compute(file), file)
        }

    /** Levels for a piece of text that has no PSI behind it, e.g. the settings preview. */
    fun ofPlainText(text: CharSequence): Index {
        val builder = Builder()
        for (i in text.indices) {
            builder.accept(i, text[i], text)
        }
        return builder.build()
    }

    private class Frame(
        @JvmField val offset: Int,
        @JvmField val char: Char,
        @JvmField val level: Int,
        @JvmField val previousSameType: Int = -1,
    )

    /** The actual bracket matching, shared by the editor and the preview. */
    private class Builder {
        private val main = HashMap<Int, Int>()
        private val angle = HashMap<Int, Int>()
        private val stack = ArrayDeque<Frame>()
        private val angleStack = ArrayDeque<Frame>()
        private val lastOpenByType = IntArray(OPENERS.length) { -1 }
        private var mainCount = 0
        private var angleCount = 0
        private var mainTruncated = false
        private var angleTruncated = false

        fun accept(offset: Int, c: Char, text: CharSequence) {
            if (OPENERS.indexOf(c) < 0 && CLOSERS.indexOf(c) < 0 && c != '<' && c != '>') {
                return
            }
            if ((c == '<' || c == '>') && isObviousComparison(text, offset)) return

            if (c == '<' || c == '>') {
                acceptAngle(offset, c)
            } else {
                acceptMain(offset, c)
            }
        }

        private fun acceptAngle(offset: Int, c: Char) {
            if (angleTruncated) return
            if (angleCount >= MAX_BRACKETS_PER_GROUP) {
                angleTruncated = true
                angle.clear()
                angleStack.clear()
                return
            }
            angleCount++

            if (c == '<') {
                angle[offset] = angleStack.size
                angleStack.addLast(Frame(offset, c, angleStack.size))
            } else {
                val open = angleStack.removeLastOrNull()
                // A stray `>` is almost always an operator - leave it alone.
                if (open != null) angle[offset] = open.level
            }
        }

        private fun acceptMain(offset: Int, c: Char) {
            if (mainTruncated) return
            if (mainCount >= MAX_BRACKETS_PER_GROUP) {
                mainTruncated = true
                main.clear()
                stack.clear()
                lastOpenByType.fill(-1)
                return
            }
            mainCount++

            val openerType = OPENERS.indexOf(c)
            if (openerType >= 0) {
                val level = stack.size
                main[offset] = level
                stack.addLast(Frame(offset, c, level, lastOpenByType[openerType]))
                lastOpenByType[openerType] = level
                return
            }

            val type = CLOSERS.indexOf(c)
            val matchIndex = lastOpenByType[type]
            if (matchIndex < 0) {
                main[offset] = UNMATCHED
            } else {
                // Everything opened above the match was never closed.
                while (stack.size > matchIndex + 1) {
                    main[removeLastMain().offset] = UNMATCHED
                }
                val open = removeLastMain()
                main[offset] = open.level
                main[open.offset] = open.level
            }
        }

        private fun removeLastMain(): Frame {
            val frame = stack.removeLast()
            lastOpenByType[OPENERS.indexOf(frame.char)] = frame.previousSameType
            return frame
        }

        fun build(): Index {
            if (!mainTruncated) {
                for (frame in stack) main[frame.offset] = UNMATCHED
            }
            if (!angleTruncated) {
                for (frame in angleStack) angle.remove(frame.offset)
            }
            return if (main.isEmpty() && angle.isEmpty()) EMPTY else Index(main, angle)
        }

        private fun isObviousComparison(text: CharSequence, offset: Int): Boolean =
            offset > 0 && offset + 1 < text.length &&
                    text[offset - 1].isWhitespace() && text[offset + 1].isWhitespace()
    }

    private fun compute(file: PsiFile): Index {
        if (isExcluded(file.language)) return EMPTY

        val text = file.viewProvider.contents
        if (text.length > MAX_FILE_LENGTH) return EMPTY

        val builder = Builder()
        val lexerCompleted = forEachCodeChar(file, text) { offset, c -> builder.accept(offset, c, text) }
        if (!lexerCompleted) return EMPTY
        return builder.build()
    }

    /**
     * Feeds every character of the file that is neither in a comment nor in a string
     * literal to [consumer].
     */
    private inline fun forEachCodeChar(
        file: PsiFile,
        text: CharSequence,
        consumer: (Int, Char) -> Unit,
    ): Boolean {
        val lexer = try {
            SyntaxHighlighterFactory
                .getSyntaxHighlighter(file.language, file.project, file.viewProvider.virtualFile)
                ?.highlightingLexer
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            null
        }

        if (lexer == null) return false

        val skipped = skippedTokens(file.language)
        return try {
            lexer.start(text)
            var charactersSinceCancellationCheck = 0
            while (true) {
                ProgressManager.checkCanceled()
                val type = lexer.tokenType ?: break
                if (!isSkipped(type, skipped)) {
                    for (i in lexer.tokenStart until lexer.tokenEnd) {
                        consumer(i, text[i])
                        charactersSinceCancellationCheck++
                        if (charactersSinceCancellationCheck >= CANCELLATION_CHECK_INTERVAL) {
                            ProgressManager.checkCanceled()
                            charactersSinceCancellationCheck = 0
                        }
                    }
                }
                lexer.advance()
            }
            true
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Walks the base language chain as well, so that dialects of an excluded language
     * (and `.md` files while the Markdown plugin is disabled) are covered too.
     */
    private fun isExcluded(language: Language): Boolean {
        var current: Language? = language
        while (current != null) {
            if (current.id in EXCLUDED_LANGUAGE_IDS) return true
            current = current.baseLanguage
        }
        return false
    }

    private fun skippedTokens(language: Language): TokenSet {
        val definition = LanguageParserDefinitions.INSTANCE.forLanguage(language) ?: return TokenSet.EMPTY
        return try {
            TokenSet.orSet(definition.commentTokens, definition.stringLiteralElements)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            TokenSet.EMPTY
        }
    }

    private fun isSkipped(type: IElementType, skipped: TokenSet): Boolean {
        if (skipped.contains(type)) return true
        // Fallback for languages whose highlighting lexer emits token types that are not
        // part of the parser definition (plain text files, template languages, ...).
        val name = type.toString().uppercase()
        return name.contains("COMMENT") || name.contains("STRING") ||
                name.contains("CHARACTER_LITERAL") || name.contains("CHAR_LITERAL") ||
                name.contains("REGEXP") || name.contains("REGEX_LITERAL") ||
                name.contains("ATTRIBUTE_VALUE") || name.contains("CDATA") ||
                name.contains("TEMPLATE_TEXT") || name == "XML_DATA_CHARACTERS"
    }
}
