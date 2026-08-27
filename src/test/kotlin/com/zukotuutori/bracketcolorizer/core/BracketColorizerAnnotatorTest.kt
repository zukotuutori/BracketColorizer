package com.zukotuutori.bracketcolorizer.core

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.zukotuutori.bracketcolorizer.settings.BracketColorizerSettings

class BracketColorizerAnnotatorTest : BasePlatformTestCase() {

    fun testEveryBracketGetsItsOwnHighlight() {
        val text = "class A { void f() { g(new int[]{1}); } }"
        myFixture.configureByText("Test.java", text)

        val highlighted = singleCharInformationOffsets()
        for (offset in text.indices) {
            if (text[offset] in "()[]{}") {
                assertTrue("no highlight for '${text[offset]}' at $offset", highlighted.contains(offset))
            }
        }
    }

    fun testNothingIsHighlightedWhenDisabled() {
        val settings = BracketColorizerSettings.instance
        val backup = settings.state.copy()
        try {
            val disabled = backup.copy()
            disabled.enabled = false
            settings.updateFrom(disabled)

            val text = "class A { void f() { } }"
            myFixture.configureByText("Test.java", text)

            val highlighted = singleCharInformationOffsets()
            for (offset in text.indices) {
                if (text[offset] in "(){}") {
                    assertFalse("unexpected highlight at $offset", highlighted.contains(offset))
                }
            }
        } finally {
            settings.updateFrom(backup)
        }
    }

    fun testNestingBelowTheLastLevelStaysUncolored() {
        val settings = BracketColorizerSettings.instance
        val backup = settings.state.copy()
        val text = "class A { void f() { g(); } }"
        val tooDeep = text.indexOf("g(") + 1 // level 2, one below the two configured levels
        try {
            val twoLevels = backup.copy()
            twoLevels.enabled = true
            twoLevels.cycleColors = false
            twoLevels.levelColors = arrayListOf("#4169E1", "#FF1493")
            settings.updateFrom(twoLevels)

            myFixture.configureByText("Test.java", text)
            val highlighted = singleCharInformationOffsets()
            assertTrue("level 1 must be colored", highlighted.contains(text.indexOf('{')))
            assertTrue("level 2 must be colored", highlighted.contains(text.indexOf("()")))
            assertFalse("level 3 must keep the normal text color", highlighted.contains(tooDeep))

            // ... unless the colors are told to repeat.
            val repeating = twoLevels.copy()
            repeating.cycleColors = true
            settings.updateFrom(repeating)

            myFixture.configureByText("Repeat.java", text)
            assertTrue("repeating colors must reach level 3", singleCharInformationOffsets().contains(tooDeep))
        } finally {
            settings.updateFrom(backup)
        }
    }

    private fun singleCharInformationOffsets(): Set<Int> =
        myFixture.doHighlighting(HighlightSeverity.INFORMATION)
            .filter { it.endOffset - it.startOffset == 1 }
            .mapTo(HashSet()) { it.startOffset }
}
