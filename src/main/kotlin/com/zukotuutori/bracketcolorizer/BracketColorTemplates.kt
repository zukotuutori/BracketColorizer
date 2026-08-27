package com.zukotuutori.bracketcolorizer

import com.intellij.util.xmlb.annotations.XCollection

/**
 * A named set of nesting colors. The built in ones are taken from well known editor color
 * schemes, the others are palettes the user saved from their own colors.
 *
 * Persisted as part of [BracketColorizerSettings.State], hence the plain fields and the
 * no-argument constructor.
 */
class BracketColorTemplate() {

    @JvmField
    var id: String = ""

    @JvmField
    var displayName: String = ""

    @XCollection(style = XCollection.Style.v2)
    @JvmField
    var levelColors: MutableList<String> = ArrayList()

    @JvmField
    var unmatchedColor: String = BracketColorizerSettings.DEFAULT_UNMATCHED_COLOR

    /**
     * The bracket settings that come with the template. Selecting it ticks these boxes in the
     * form; they stay editable afterwards, so a deviation is possible and simply not saved back.
     * The defaults are how the built in templates behave: the three unambiguous bracket types,
     * repeating colors - the schemes only define three to six - and unmatched brackets marked.
     */
    @JvmField
    var colorRound: Boolean = true

    @JvmField
    var colorSquare: Boolean = true

    @JvmField
    var colorCurly: Boolean = true

    @JvmField
    var colorAngle: Boolean = false

    @JvmField
    var cycleColors: Boolean = true

    @JvmField
    var boldBrackets: Boolean = false

    @JvmField
    var highlightUnmatched: Boolean = true

    /** Built in templates come back with "Restore defaults", saved ones do not. */
    @JvmField
    var builtIn: Boolean = false

    constructor(
        id: String,
        displayName: String,
        levelColors: List<String>,
        unmatchedColor: String,
        builtIn: Boolean = false,
    ) : this() {
        this.id = id
        this.displayName = displayName
        this.levelColors = ArrayList(levelColors)
        this.unmatchedColor = unmatchedColor
        this.builtIn = builtIn
    }

    fun copy(): BracketColorTemplate =
        BracketColorTemplate(id, displayName, levelColors, unmatchedColor, builtIn).also {
            it.colorRound = colorRound
            it.colorSquare = colorSquare
            it.colorCurly = colorCurly
            it.colorAngle = colorAngle
            it.cycleColors = cycleColors
            it.boldBrackets = boldBrackets
            it.highlightUnmatched = highlightUnmatched
        }

    /** Part of [BracketColorizerSettings.State.signature], so renames are noticed too. */
    fun signature(): String = listOf(
        id, displayName, unmatchedColor, levelColors.joinToString(","),
        colorRound, colorSquare, colorCurly, colorAngle,
        cycleColors, boldBrackets, highlightUnmatched,
    ).joinToString(":")

    /** What the combo box and the list in the manager dialog show. */
    override fun toString(): String = displayName

    companion object {
        /**
         * Sources: the VS Code entries are the platform defaults from
         * `src/vs/editor/common/core/editorColorRegistry.ts`, the others are the
         * `editorBracketHighlight.foreground*` keys of the respective themes.
         */
        val BUILT_IN: List<BracketColorTemplate> = listOf(
            BracketColorTemplate(
                "vscode-dark",
                "VS Code Dark+",
                listOf("#FFD700", "#DA70D6", "#179FFF"),
                "#FF1212",
                builtIn = true,
            ),
            BracketColorTemplate(
                "vscode-light",
                "VS Code Light+",
                listOf("#0431FA", "#319331", "#7B3814"),
                "#FF1212",
                builtIn = true,
            ),
            BracketColorTemplate(
                "vscode-hc-dark",
                "VS Code High Contrast Dark",
                listOf("#FFD700", "#DA70D6", "#87CEFA"),
                "#FF3232",
                builtIn = true,
            ),
            BracketColorTemplate(
                "one-dark-pro",
                "One Dark Pro",
                listOf("#D19A66", "#C678DD", "#56B6C2"),
                "#FF1212",
                builtIn = true,
            ),
            BracketColorTemplate(
                "dracula",
                "Dracula",
                listOf("#F8F8F2", "#FF79C6", "#8BE9FD", "#50FA7B", "#BD93F9", "#FFB86C"),
                "#FF5555",
                builtIn = true,
            ),
            BracketColorTemplate(
                "nord",
                "Nord",
                listOf("#8FBCBB", "#88C0D0", "#81A1C1", "#5E81AC"),
                "#BF616A",
                builtIn = true,
            ),
            BracketColorTemplate(
                "viasfora-rainbow",
                "Rainbow (Viasfora)",
                BracketColorizerSettings.DEFAULT_LEVEL_COLORS,
                BracketColorizerSettings.DEFAULT_UNMATCHED_COLOR,
                builtIn = true,
            ),
        )

        fun defaults(): MutableList<BracketColorTemplate> = BUILT_IN.mapTo(ArrayList()) { it.copy() }

        /**
         * [templates] with every built in entry back the way it shipped and the missing ones
         * appended. Templates the user saved themselves are passed through untouched.
         */
        fun withDefaultsRestored(templates: List<BracketColorTemplate>): List<BracketColorTemplate> {
            val restored = templates.map { current ->
                BUILT_IN.firstOrNull { it.id == current.id }?.copy() ?: current.copy()
            }
            return restored + BUILT_IN.filter { builtIn -> restored.none { it.id == builtIn.id } }.map { it.copy() }
        }
    }
}
