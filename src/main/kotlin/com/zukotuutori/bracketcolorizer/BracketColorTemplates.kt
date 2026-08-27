package com.zukotuutori.bracketcolorizer

/**
 * A ready made set of nesting colors, taken from a well known editor color scheme.
 *
 * The color schemes define three to six bracket colors; deeper nesting is covered by the
 * "repeat the colors" option, exactly like in the editors the schemes come from.
 */
data class BracketColorTemplate(
    val id: String,
    val displayName: String,
    val levelColors: List<String>,
    val unmatchedColor: String,
) {
    override fun toString(): String = displayName

    companion object {
        /**
         * Sources: the VS Code entries are the platform defaults from
         * `src/vs/editor/common/core/editorColorRegistry.ts`, the others are the
         * `editorBracketHighlight.foreground*` keys of the respective themes.
         */
        val ALL: List<BracketColorTemplate> = listOf(
            BracketColorTemplate(
                "vscode-dark",
                "VS Code Dark+",
                listOf("#FFD700", "#DA70D6", "#179FFF"),
                "#FF1212",
            ),
            BracketColorTemplate(
                "vscode-light",
                "VS Code Light+",
                listOf("#0431FA", "#319331", "#7B3814"),
                "#FF1212",
            ),
            BracketColorTemplate(
                "vscode-hc-dark",
                "VS Code High Contrast Dark",
                listOf("#FFD700", "#DA70D6", "#87CEFA"),
                "#FF3232",
            ),
            BracketColorTemplate(
                "one-dark-pro",
                "One Dark Pro",
                listOf("#D19A66", "#C678DD", "#56B6C2"),
                "#FF1212",
            ),
            BracketColorTemplate(
                "dracula",
                "Dracula",
                listOf("#F8F8F2", "#FF79C6", "#8BE9FD", "#50FA7B", "#BD93F9", "#FFB86C"),
                "#FF5555",
            ),
            BracketColorTemplate(
                "nord",
                "Nord",
                listOf("#8FBCBB", "#88C0D0", "#81A1C1", "#5E81AC"),
                "#BF616A",
            ),
            BracketColorTemplate(
                "viasfora-rainbow",
                "Rainbow (Viasfora)",
                BracketColorizerSettings.DEFAULT_LEVEL_COLORS,
                BracketColorizerSettings.DEFAULT_UNMATCHED_COLOR,
            ),
        )

        val DEFAULT: BracketColorTemplate = ALL[0]

        fun byId(id: String?): BracketColorTemplate? = ALL.firstOrNull { it.id == id }
    }
}
