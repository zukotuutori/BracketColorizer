package com.zukotuutori.bracketcolorizer.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.zukotuutori.bracketcolorizer.settings.BracketColorizerListener
import com.zukotuutori.bracketcolorizer.settings.BracketColorizerSettings
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Puts the configuration form into a tool window (right hand side stripe by default),
 * so the colors can be tweaked while looking at the code. Changes are applied live,
 * with a short delay so that dragging a spinner does not re-highlight on every step.
 */
class BracketColorizerToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val settings = BracketColorizerSettings.instance
        val disposable = Disposer.newDisposable("BracketColorizerToolWindow")
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable)

        lateinit var form: BracketColorizerSettingsPanel
        form = BracketColorizerSettingsPanel {
            alarm.cancelAllRequests()
            alarm.addRequest({
                val state = form.readState()
                if (state.signature() != settings.state.signature()) {
                    settings.applyAndRefresh(state)
                }
            }, APPLY_DELAY_MS)
        }

        val hint = JBLabel("Changes are applied immediately.").apply {
            foreground = UIUtil.getContextHelpForeground()
            border = JBUI.Borders.empty(8, 12, 0, 12)
        }
        val wrapper = JPanel(BorderLayout()).apply {
            add(hint, BorderLayout.NORTH)
            add(form, BorderLayout.CENTER)
        }
        val scrollPane = JBScrollPane(wrapper).apply { border = JBUI.Borders.empty() }

        val content = ContentFactory.getInstance().createContent(scrollPane, null, false)
        content.setDisposer(disposable)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)

        // Follow changes made in the settings dialog while the tool window is open.
        ApplicationManager.getApplication().messageBus.connect(disposable)
            .subscribe(BracketColorizerSettings.TOPIC, object : BracketColorizerListener {
                override fun settingsChanged() {
                    if (form.readState().signature() != settings.state.signature()) {
                        form.writeState(settings.state)
                    }
                }
            })
    }

    private companion object {
        const val APPLY_DELAY_MS = 250
    }
}
