package com.zukotuutori.bracketcolorizer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBLabel
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.util.xmlb.XmlSerializer
import javax.swing.JButton
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
        val template = BracketColorTemplate.BUILT_IN.first { it.id == "vscode-dark" }
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

    fun testSavedTemplateSurvivesTheStateRoundTrip() {
        val saved = BracketColorTemplate("custom-1", "My colors 1", listOf("#010203", "#040506"), "#070809")
        val panel = BracketColorizerSettingsPanel { }
        val state = BracketColorizerSettings.State().apply {
            templates.add(saved)
            useTemplate = true
            templateId = saved.id
        }

        panel.writeState(state)
        val read = panel.readState()

        assertEquals(state.signature(), read.signature())
        assertEquals(saved.levelColors, read.effectiveLevelColors())
        assertFalse("saved templates are not restored as defaults", read.templates.single { it.id == saved.id }.builtIn)
    }

    fun testDeletedTemplatesFallBackToTheCustomColors() {
        val state = BracketColorizerSettings.State().apply {
            levelColors = arrayListOf("#112233")
            useTemplate = true
            templateId = BracketColorTemplate.BUILT_IN[0].id
            templates.clear()
        }

        assertNull(state.template())
        assertEquals(listOf("#112233"), state.effectiveLevelColors())
    }

    fun testRestoringDefaultsKeepsTheSavedTemplatesAndUndoesEdits() {
        val saved = BracketColorTemplate("custom-1", "My colors 1", listOf("#010203"), "#070809")
        val editedBuiltIn = BracketColorTemplate.BUILT_IN[0].copy().apply {
            displayName = "Renamed"
            levelColors = arrayListOf("#FFFFFF")
        }

        val restored = BracketColorTemplate.withDefaultsRestored(listOf(saved, editedBuiltIn))

        assertEquals(BracketColorTemplate.BUILT_IN.size + 1, restored.size)
        assertEquals(saved.signature(), restored.single { it.id == saved.id }.signature())
        assertEquals(
            BracketColorTemplate.BUILT_IN[0].signature(),
            restored.single { it.id == BracketColorTemplate.BUILT_IN[0].id }.signature(),
        )
    }

    fun testTemplateEditorKeepsTheIdentityOfTheEditedTemplate() {
        val original = BracketColorTemplate.BUILT_IN[0]
        val dialog = TemplateEditorDialog(JPanel(), original, listOf("Taken"))
        try {
            val nameField = dialog.centerPanel.descendants().filterIsInstance<JBTextField>().first()

            assertEquals(original.displayName, nameField.text)
            assertEquals(original.id, dialog.result.id)
            assertTrue("an edited built in template stays built in", dialog.result.builtIn)
            assertEquals(original.levelColors, dialog.result.levelColors)

            nameField.text = "Taken"
            assertNotNull("duplicate names are rejected", dialog.doValidate())
            nameField.text = "  "
            assertNotNull("blank names are rejected", dialog.doValidate())
            nameField.text = "Something else"
            assertNull(dialog.doValidate())
        } finally {
            Disposer.dispose(dialog.disposable)
        }
    }

    fun testManagerDialogDeletesAllAndRestoresDefaults() {
        val saved = BracketColorTemplate("custom-1", "My colors 1", listOf("#010203"), "#070809")
        val panel = JPanel()
        val dialog = TemplateManagerDialog(panel, BracketColorTemplate.defaults() + saved)
        try {
            val buttons = dialog.centerPanel.descendants()
                .filterIsInstance<JButton>()
                .associateBy { it.text }

            assertFalse("nothing is selected yet", buttons.getValue("Change template...").isEnabled)
            dialog.centerPanel.descendants().filterIsInstance<JBList<*>>().first().selectedIndex = 0
            assertTrue(buttons.getValue("Change template...").isEnabled)

            buttons.getValue("Delete all").doClick()
            assertTrue(dialog.templates.isEmpty())

            buttons.getValue("Restore defaults").doClick()
            assertEquals(BracketColorTemplate.BUILT_IN.map { it.id }, dialog.templates.map { it.id })
        } finally {
            Disposer.dispose(dialog.disposable)
        }
    }

    fun testSavedTemplatesSurviveSerialization() {
        val state = BracketColorizerSettings.State().apply {
            templates.add(
                BracketColorTemplate("custom-1", "My colors 1", listOf("#010203", "#040506"), "#070809").also {
                    it.colorAngle = true
                    it.cycleColors = false
                    it.boldBrackets = true
                    it.highlightUnmatched = false
                },
            )
            useTemplate = true
            templateId = "custom-1"
        }

        val restored = XmlSerializer.deserialize(
            XmlSerializer.serialize(state),
            BracketColorizerSettings.State::class.java,
        )

        assertEquals(state.signature(), restored.signature())
        assertEquals(listOf("#010203", "#040506"), restored.effectiveLevelColors())
    }

    fun testSelectingATemplateSeedsItsBracketSettings() {
        val seeded = BracketColorTemplate("custom-1", "Seeded", listOf("#010203"), "#070809").also {
            it.colorAngle = true
            it.cycleColors = false
            it.boldBrackets = true
            it.highlightUnmatched = false
        }
        val panel = BracketColorizerSettingsPanel { }
        panel.writeState(BracketColorizerSettings.State().apply {
            templates.add(seeded)
            useTemplate = true
            templateId = BracketColorTemplate.BUILT_IN[0].id
        })

        // The panel works on copies, and a combo box rejects an item that is not in its model.
        val combo = panel.descendants().filterIsInstance<ComboBox<*>>().first()
        combo.selectedItem = (0 until combo.model.size)
            .map { combo.model.getElementAt(it) }
            .filterIsInstance<BracketColorTemplate>()
            .single { it.id == seeded.id }
        val state = panel.readState()

        assertTrue("the template's settings are taken over", state.colorAngle)
        assertFalse(state.cycleColors)
        assertTrue(state.boldBrackets)
        assertFalse(state.highlightUnmatched)

        // Seeded, not owned: the boxes stay editable and the deviation is not saved back.
        panel.descendants().filterIsInstance<JBCheckBox>().first { it.text == "Draw brackets in bold" }.doClick()
        assertFalse(panel.readState().boldBrackets)
        assertTrue(panel.readState().templates.single { it.id == seeded.id }.boldBrackets)
    }

    fun testPickingABracketTypeTurnsColorizingBackOn() {
        val panel = BracketColorizerSettingsPanel { }
        panel.writeState(BracketColorizerSettings.State())
        val boxes = panel.descendants().filterIsInstance<JBCheckBox>().associateBy { it.text }
        val master = boxes.getValue("Enable bracket colorizing")

        for (type in listOf("( )", "[ ]", "{ }")) boxes.getValue(type).doClick()
        assertFalse("nothing left to color", master.isSelected)
        assertFalse(master.isEnabled)

        boxes.getValue("[ ]").doClick()
        assertTrue("the first bracket type switches colorizing back on", master.isSelected)
        assertTrue(master.isEnabled)
        assertTrue(panel.readState().enabled)
    }

    fun testTheMasterSwitchIsRedWhileColorizingIsOff() {
        val panel = BracketColorizerSettingsPanel { }
        panel.writeState(BracketColorizerSettings.State())
        val master = panel.descendants()
            .filterIsInstance<JBCheckBox>()
            .first { it.text == "Enable bracket colorizing" }

        val whileOn = master.foreground
        master.doClick()

        val whileOff = master.foreground
        assertFalse(master.isSelected)
        assertTrue("the label turns red", whileOff.red > whileOff.green && whileOff.red > whileOff.blue)
        assertFalse("and is plain again while on", whileOn == whileOff)
    }

    fun testWritingAStateKeepsItsOwnEnabledFlag() {
        val panel = BracketColorizerSettingsPanel { }
        val boxes = panel.descendants().filterIsInstance<JBCheckBox>().associateBy { it.text }
        // Leaves the form in the state where the master switch is forced off and disabled.
        for (type in listOf("( )", "[ ]", "{ }")) boxes.getValue(type).doClick()

        panel.writeState(BracketColorizerSettings.State().apply { enabled = false })

        assertFalse("a written state is not a user click", panel.readState().enabled)
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
        assertTrue("picking a bracket type again switches colorizing back on", master.isSelected)
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
