package com.zukotuutori.bracketcolorizer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import javax.swing.JPanel

class BracketColorizerToolWindowTest : BasePlatformTestCase() {

    fun testToolWindowIsRegisteredOnTheRight() {
        val ep = ToolWindowEP.EP_NAME.extensionList.single { it.id == "Bracket Colorizer" }

        assertEquals("right", ep.anchor)
        assertEquals(BracketColorizerToolWindowFactory::class.java.name, ep.factoryClass)
        assertEquals("/icons/bracketColorizer.svg", ep.icon)
        assertNotNull(
            "icon referenced from plugin.xml is missing",
            javaClass.getResource("/icons/bracketColorizer.svg"),
        )
    }

    fun testFormRoundTripsTheState() {
        val panel = BracketColorizerSettingsPanel { }
        val state = BracketColorizerSettings.State()
        state.colorAngle = true
        state.boldBrackets = true
        state.cycleColors = false
        state.unmatchedColor = "#123456"
        state.levelColors = arrayListOf("#112233", "#445566", "#778899")

        panel.writeState(state)

        assertEquals(state.signature(), panel.readState().signature())
    }

    fun testTemplateColorsWinOverTheCustomOnes() {
        val template = BracketColorTemplate.byId("vscode-dark")!!
        val panel = BracketColorizerSettingsPanel { }
        val state = BracketColorizerSettings.State().apply {
            levelColors = arrayListOf("#112233")
            unmatchedColor = "#123456"
            useTemplate = true
            templateId = template.id
        }

        panel.writeState(state)

        assertEquals(state.signature(), panel.readState().signature())
        assertEquals(template.levelColors, state.effectiveLevelColors())
        assertEquals(template.unmatchedColor, state.effectiveUnmatchedColor())
        assertEquals(ColorHex.parse(template.levelColors[1]), state.colorAt(1))
        // The hand picked colors survive the detour through the template.
        assertEquals(listOf("#112233"), state.copy().levelColors)
    }

    fun testOversizedPaletteIsCapped() {
        val panel = BracketColorizerSettingsPanel { }
        val state = BracketColorizerSettings.State().apply {
            levelColors = MutableList(1_000) { "#112233" }
        }

        panel.writeState(state)

        assertEquals(BracketColorizerSettings.MAX_LEVEL_COLORS, panel.readState().levelColors.size)
        assertEquals(BracketColorizerSettings.MAX_LEVEL_COLORS, state.copy().levelColors.size)
    }

    fun testUnmatchedColorIsOnlyVisibleWhenHighlightingIsSelected() {
        val panel = BracketColorizerSettingsPanel { }
        val state = BracketColorizerSettings.State().apply {
            highlightUnmatched = false
        }
        panel.writeState(state)

        val checkBox = panel.descendants()
            .filterIsInstance<JBCheckBox>()
            .single { it.text == "Highlight unmatched brackets" }
        val colorRow = panel.descendants()
            .filterIsInstance<JPanel>()
            .single { row ->
                row.components.filterIsInstance<JBLabel>().any { it.text == "Color" }
            }

        assertFalse(colorRow.isVisible)
        checkBox.doClick()
        assertTrue(colorRow.isVisible)
        checkBox.doClick()
        assertFalse(colorRow.isVisible)
    }

    fun testMasterSwitchIsUnavailableWhenNoBracketTypesAreSelected() {
        val panel = BracketColorizerSettingsPanel { }
        val state = BracketColorizerSettings.State().apply {
            enabled = true
            colorRound = true
            colorSquare = false
            colorCurly = false
            colorAngle = false
        }
        panel.writeState(state)

        val checkBoxes = panel.descendants()
            .filterIsInstance<JBCheckBox>()
            .associateBy { it.text }
        val master = checkBoxes.getValue("Enable bracket colorizing")
        val round = checkBoxes.getValue("( )")

        assertTrue(master.isSelected)
        assertTrue(master.isEnabled)

        round.doClick()

        assertFalse(master.isSelected)
        assertFalse(master.isEnabled)
        assertTrue("Bracket types must stay selectable so the UI can recover", round.isEnabled)

        round.doClick()

        assertTrue(master.isEnabled)
        assertFalse(master.isSelected)
    }

    fun testApplyAndRefreshNotifiesListeners() {
        val settings = BracketColorizerSettings.instance
        val backup = settings.state.copy()
        var notified = 0
        ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
            .subscribe(BracketColorizerSettings.TOPIC, object : BracketColorizerListener {
                override fun settingsChanged() {
                    notified++
                }
            })
        try {
            val changed = backup.copy()
            changed.levelColors = arrayListOf("#112233")
            settings.applyAndRefresh(changed)

            assertEquals(1, notified)
            assertEquals("#112233", settings.state.levelColors.single())
        } finally {
            settings.updateFrom(backup)
        }
    }

    private fun Component.descendants(): Sequence<Component> = sequence {
        yield(this@descendants)
        if (this@descendants is Container) {
            for (child in components) yieldAll(child.descendants())
        }
    }
}
