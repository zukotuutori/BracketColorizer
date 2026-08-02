package com.zukotuutori.bracketcolorizer

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

/**
 * Settings | Editor | Bracket Colorizer
 *
 * The same form is also available as a tool window, see [BracketColorizerToolWindowFactory].
 */
class BracketColorizerConfigurable : Configurable {

    private val settings = BracketColorizerSettings.instance
    private var panel: BracketColorizerSettingsPanel? = null

    override fun getDisplayName(): String = "Bracket Colorizer"

    override fun createComponent(): JComponent =
        BracketColorizerSettingsPanel { }.also { panel = it }

    override fun isModified(): Boolean {
        val panel = panel ?: return false
        return panel.readState().signature() != settings.state.signature()
    }

    override fun apply() {
        panel?.let { settings.applyAndRefresh(it.readState()) }
    }

    override fun reset() {
        panel?.writeState(settings.state)
    }

    override fun disposeUIResources() {
        panel = null
    }
}
