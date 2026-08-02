package com.zukotuutori.bracketcolorizer

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.XCollection
import java.awt.Color

/** Notified after the settings changed, so that open settings UIs can follow along. */
interface BracketColorizerListener {
    fun settingsChanged()
}

/**
 * Application level, persisted configuration of the plugin.
 */
@Service(Service.Level.APP)
@State(name = "BracketColorizerSettings", storages = [Storage("bracketColorizer.xml")])
class BracketColorizerSettings : PersistentStateComponent<BracketColorizerSettings.State> {

    class State {
        @JvmField
        var enabled: Boolean = true

        @JvmField
        var colorRound: Boolean = true

        @JvmField
        var colorSquare: Boolean = true

        @JvmField
        var colorCurly: Boolean = true

        /** Angle brackets are ambiguous (`a < b`), therefore off by default. */
        @JvmField
        var colorAngle: Boolean = false

        @JvmField
        var boldBrackets: Boolean = false

        /**
         * Start over with the first color once the nesting is deeper than the color list.
         * When off, brackets below the last configured level are not colorized at all.
         */
        @JvmField
        var cycleColors: Boolean = false

        @JvmField
        var highlightUnmatched: Boolean = true

        @JvmField
        var unmatchedColor: String = DEFAULT_UNMATCHED_COLOR

        @XCollection(style = XCollection.Style.v2)
        @JvmField
        var levelColors: MutableList<String> = DEFAULT_LEVEL_COLORS.toMutableList()

        /**
         * The color of a zero based nesting level, or `null` when that level is not
         * colorized - which is the case for nesting deeper than the configured list
         * while the colors do not cycle.
         */
        fun colorAt(level: Int): Color? {
            if (levelColors.isEmpty()) return null
            val index = if (cycleColors) level % levelColors.size else level
            if (index >= levelColors.size) return null
            return ColorHex.parse(levelColors[index]) ?: ColorHex.parse(DEFAULT_LEVEL_COLORS[0])
        }

        /** Everything that is user visible, as a single string - used for change detection. */
        fun signature(): String = listOf(
            enabled, colorRound, colorSquare, colorCurly, colorAngle,
            boldBrackets, cycleColors, highlightUnmatched, unmatchedColor,
            levelColors.joinToString(",")
        ).joinToString("|")

        fun copy(): State {
            val copy = State()
            copy.enabled = enabled
            copy.colorRound = colorRound
            copy.colorSquare = colorSquare
            copy.colorCurly = colorCurly
            copy.colorAngle = colorAngle
            copy.boldBrackets = boldBrackets
            copy.cycleColors = cycleColors
            copy.highlightUnmatched = highlightUnmatched
            copy.unmatchedColor = unmatchedColor
            val colors = levelColors.take(MAX_LEVEL_COLORS)
            copy.levelColors = if (colors.isEmpty()) {
                DEFAULT_LEVEL_COLORS.toMutableList()
            } else {
                ArrayList(colors)
            }
            return copy
        }
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state.copy()
    }

    fun updateFrom(state: State) {
        myState = state.copy()
    }

    /**
     * Stores [state], re-highlights all open editors and notifies every open settings UI.
     * Must be called on the EDT.
     */
    fun applyAndRefresh(state: State) {
        updateFrom(state)
        for (project in ProjectManager.getInstance().openProjects) {
            if (!project.isDisposed) {
                DaemonCodeAnalyzer.getInstance(project).settingsChanged()
            }
        }
        ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).settingsChanged()
    }

    /** The bracket characters the user wants to see colorized. */
    fun activeBracketChars(): String = buildString {
        if (myState.colorRound) append("()")
        if (myState.colorSquare) append("[]")
        if (myState.colorCurly) append("{}")
        if (myState.colorAngle) append("<>")
    }

    /** Color for a zero based nesting level, `null` when that level stays uncolored. */
    fun colorForLevel(level: Int): Color? = myState.colorAt(level)

    fun unmatchedColor(): Color =
        ColorHex.parse(myState.unmatchedColor) ?: ColorHex.parse(DEFAULT_UNMATCHED_COLOR)!!

    companion object {
        /**
         * The nine rainbow colors Viasfora uses by default
         * (src/Viasfora.Rainbow/EditorFormats/RainbowFormats.cs), with orange and royal blue
         * swapped: a warm color on the outermost level reads like a warning, a cool one does not.
         */
        val DEFAULT_LEVEL_COLORS: List<String> = listOf(
            "#4169E1", // royal blue
            "#FF1493", // deep pink
            "#9ACD32", // yellow green
            "#9400D3", // dark violet
            "#696969", // dim gray
            "#FF9900", // orange
            "#DC143C", // crimson
            "#00CED1", // dark turquoise
            "#008000", // green
        )

        const val DEFAULT_UNMATCHED_COLOR: String = "#FF4A4A"
        const val MAX_LEVEL_COLORS: Int = 64

        @JvmField
        val TOPIC: Topic<BracketColorizerListener> =
            Topic.create("Bracket Colorizer settings", BracketColorizerListener::class.java)

        @JvmStatic
        val instance: BracketColorizerSettings
            get() = ApplicationManager.getApplication().getService(BracketColorizerSettings::class.java)
    }
}

/** Hex <-> [Color] helpers shared by the annotator and the settings UI. */
object ColorHex {

    fun parse(text: String?): Color? {
        val raw = text?.trim()?.removePrefix("#") ?: return null
        if (raw.length != 6 && raw.length != 3) return null
        if (!raw.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
        val normalized = if (raw.length == 3) raw.map { "$it$it" }.joinToString("") else raw
        return try {
            Color(normalized.toInt(16))
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun format(color: Color): String = String.format("#%02X%02X%02X", color.red, color.green, color.blue)
}
