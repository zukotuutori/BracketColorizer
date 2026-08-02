package com.zukotuutori.bracketcolorizer

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BracketIndexTest : BasePlatformTestCase() {

    fun testNestingLevels() {
        val text = "class A { void f() { g(h[0]); } }"
        val index = index(text)

        assertEquals(0, index.main.levelAt(text.indexOf('{')))
        assertEquals(1, index.main.levelAt(text.indexOf("()")))
        assertEquals(1, index.main.levelAt(text.indexOf("()") + 1))
        assertEquals(1, index.main.levelAt(text.indexOf("{ g")))
        assertEquals(2, index.main.levelAt(text.indexOf("(h")))
        assertEquals(3, index.main.levelAt(text.indexOf('[')))
        assertEquals(3, index.main.levelAt(text.indexOf(']')))
        assertEquals(2, index.main.levelAt(text.indexOf(");")))
        assertEquals(1, index.main.levelAt(text.indexOf("} }")))
        assertEquals(0, index.main.levelAt(text.lastIndexOf('}')))
    }

    fun testBracketsInStringsAndCommentsAreIgnored() {
        val text = "class A { String s = \"((([\"; /* )) */ }"
        val index = index(text)

        assertEquals(0, index.main.levelAt(text.indexOf('{')))
        assertEquals(0, index.main.levelAt(text.lastIndexOf('}')))
        for (offset in text.indexOf('"')..text.lastIndexOf('"')) {
            assertNull("bracket inside a string literal must not be colorized", index.main[offset])
        }
        assertNull("bracket inside a comment must not be colorized", index.main[text.indexOf(")) */")])
    }

    fun testUnmatchedCloser() {
        val text = "class A { } }"
        val index = index(text)

        assertEquals(0, index.main.levelAt(text.indexOf('{')))
        assertEquals(0, index.main.levelAt(text.indexOf('}')))
        assertEquals(BracketIndex.UNMATCHED, index.main.levelAt(text.lastIndexOf('}')))
    }

    fun testUnclosedOpener() {
        val text = "class A { void f( { } }"
        val index = index(text)

        assertEquals(BracketIndex.UNMATCHED, index.main.levelAt(text.indexOf("f(") + 1))
        assertEquals(0, index.main.levelAt(text.lastIndexOf('}')))
    }

    fun testMismatchedNestingRecoversAtTheNearestMatchingType() {
        val text = "([)]"
        val index = BracketIndex.ofPlainText(text)

        assertEquals(0, index.main.levelAt(0))
        assertEquals(BracketIndex.UNMATCHED, index.main.levelAt(1))
        assertEquals(0, index.main.levelAt(2))
        assertEquals(BracketIndex.UNMATCHED, index.main.levelAt(3))
    }

    fun testAngleBracketsUseTheirOwnStack() {
        val text = "class A { Map<String, List<Integer>> m; void f(int a) { } }"
        val index = index(text)

        assertEquals(0, index.angle.levelAt(text.indexOf('<')))
        assertEquals(1, index.angle.levelAt(text.indexOf("<Integer")))
        assertEquals(1, index.angle.levelAt(text.indexOf(">>")))
        assertEquals(0, index.angle.levelAt(text.indexOf(">>") + 1))
        // Angle brackets must not shift the nesting level of the real brackets.
        assertEquals(1, index.main.levelAt(text.indexOf("(int")))
        assertEquals(1, index.main.levelAt(text.indexOf("{ }")))
    }

    fun testComparisonOperatorIsNotColorized() {
        val text = "class A { boolean f(int a, int b) { return a > b; } }"
        val index = index(text)

        assertNull("a stray > is an operator, not a bracket", index.angle[text.indexOf("> b")])
        assertEquals(1, index.main.levelAt(text.indexOf("(int")))
    }

    fun testSpacedComparisonOperatorsAreNotColorized() {
        val text = "class A { boolean f(int a, int b, int c, int d) { return a < b && c > d; } }"
        val index = index(text)

        assertNull(index.angle[text.indexOf("< b")])
        assertNull(index.angle[text.indexOf("> d")])
    }

    fun testMarkupTextAndAttributeValuesAreIgnored() {
        val text = "<root value=\"([ignored])\">text {ignored}</root>"
        val index = BracketIndex.of(myFixture.configureByText("Test.xml", text))

        val attributeStart = text.indexOf('(')
        val attributeEnd = text.indexOf(']')
        for (offset in attributeStart..attributeEnd) {
            if (text[offset] in "()[]{}") assertNull(index.main[offset])
        }
        val textStart = text.indexOf("{ignored}")
        assertNull(index.main[textStart])
        assertNull(index.main[textStart + "{ignored}".lastIndex])
    }

    fun testPathologicalMismatchInputStaysFastAndTheLimitIsAllOrNothing() {
        val atLimit = "(".repeat(100_000) + "]".repeat(100_000)
        val startedAt = System.nanoTime()
        val index = BracketIndex.ofPlainText(atLimit)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals(200_000, index.main.size)
        assertTrue("matching took $elapsedMillis ms", elapsedMillis < 3_000)
        assertTrue(BracketIndex.ofPlainText(atLimit + "(").isEmpty)
    }

    fun testAngleLimitDoesNotDiscardMainBracketPairs() {
        val text = "(" + "<".repeat(200_001) + ")"
        val index = BracketIndex.ofPlainText(text)

        assertEquals(0, index.main.levelAt(0))
        assertEquals(0, index.main.levelAt(text.lastIndex))
        assertTrue(index.angle.isEmpty())
    }

    private fun index(text: String): BracketIndex.Index =
        BracketIndex.of(myFixture.configureByText("Test.java", text))

    private fun Map<Int, Int>.levelAt(offset: Int): Int =
        this[offset] ?: error("no bracket indexed at offset $offset")
}
