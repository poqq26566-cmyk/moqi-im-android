package com.moqi.im.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class T9PinyinTest {
    @Test
    fun segmentDigits_keepsUnseparatedXianAsOneSegment() {
        assertEquals(listOf("9426"), T9Pinyin.segmentDigits("9426"))
        assertEquals("xian", T9Pinyin.defaultPinyinFor("9426"))
        assertEquals("xian", T9Pinyin.defaultCompositionForDigits("9426"))
        assertEquals("9426", T9Pinyin.t9SchemaInputForDigits("9426"))
    }

    @Test
    fun segmentDigits_splitsOnOneAndBuildsSeparatedPinyinForRime() {
        assertEquals(listOf("94", "26"), T9Pinyin.segmentDigits("94126"))
        assertEquals("xi", T9Pinyin.defaultPinyinFor("94"))
        assertEquals("an", T9Pinyin.defaultPinyinFor("26"))
        assertEquals("xi'an", T9Pinyin.defaultCompositionForDigits("94126"))
        assertEquals("94126", T9Pinyin.t9SchemaInputForDigits("94126"))
    }

    @Test
    fun optionsFor_singleDigitShowsLettersForRemainingSegment() {
        assertEquals(listOf("m", "n", "o"), T9Pinyin.optionsFor("6"))
    }

    @Test
    fun prefixForDigits_trimsCandidatePinyinToTypedT9Prefix() {
        assertEquals("z", T9Pinyin.prefixForDigits("zai", "9"))
        assertEquals("xi", T9Pinyin.prefixForDigits("xian", "94"))
        assertEquals("xian", T9Pinyin.prefixForDigits("xian", "9426"))
    }

    @Test
    fun digitsFor_selectedShortPinyinConsumesOnlyItsPrefix() {
        assertEquals("92", T9Pinyin.digitsFor("wa"))
        assertTrue("926".startsWith(T9Pinyin.digitsFor("wa")))
        assertEquals("6", "926".drop(T9Pinyin.digitsFor("wa").length))
    }

    @Test
    fun selectionState_replaysSelectedNiHaoMaToRime() {
        val state = T9PinyinSelectionState()
        "6442662".forEach(state::appendDigit)

        assertEquals(listOf("64", "426", "62"), state.segments())
        assertTrue(state.options().contains("ni"))

        assertEquals("ni'426'62", state.selectPinyin("ni"))
        assertEquals("ni'gan'ma", state.displayText())
        assertEquals(1, state.activeSegmentIndex)
        assertTrue(state.options().contains("hao"))

        assertEquals("ni'hao'62", state.selectPinyin("hao"))
        assertEquals("ni'hao'ma", state.displayText())
        assertEquals(2, state.activeSegmentIndex)
        assertTrue(state.options().contains("ma"))

        assertEquals("ni'hao'ma", state.selectPinyin("ma"))
        assertEquals("ni'hao'ma", state.replayText())
    }

    @Test
    fun selectionState_resegmentsTailAfterSelectingPrefix() {
        val state = T9PinyinSelectionState()
        "4343".forEach(state::appendDigit)

        assertEquals(listOf("434", "3"), state.segments())
        assertTrue(state.options().contains("ge"))

        assertEquals("ge'43", state.selectPinyin("ge"))
        assertEquals("ge'ge", state.displayText())
        assertEquals(listOf("43", "43"), state.segments())
        assertEquals(1, state.activeSegmentIndex)
        assertTrue(state.options().contains("ge"))

        assertEquals("ge'ge", state.selectPinyin("ge"))
        assertEquals(2, state.activeSegmentIndex)
        assertTrue(state.options().isEmpty())
    }

    @Test
    fun selectionState_clearsOptionsAfterSelectingAllRepeatedGeSegments() {
        val state = T9PinyinSelectionState()
        "434343".forEach(state::appendDigit)

        assertTrue(state.options().contains("ge"))

        assertEquals("ge'434'3", state.selectPinyin("ge"))
        assertEquals(1, state.activeSegmentIndex)
        assertTrue(state.options().contains("ge"))

        assertEquals("ge'ge'43", state.selectPinyin("ge"))
        assertEquals(2, state.activeSegmentIndex)
        assertTrue(state.options().contains("ge"))

        assertEquals("ge'ge'ge", state.selectPinyin("ge"))
        assertEquals(3, state.activeSegmentIndex)
        assertTrue(state.options().isEmpty())
    }

    @Test
    fun selectionState_resegmentsLongTailAfterRepeatedPrefixSelections() {
        val state = T9PinyinSelectionState()
        "4343486542".forEach(state::appendDigit)

        assertTrue(state.options().contains("ge"))

        state.selectPinyin("ge")
        assertTrue(state.options().contains("ge"))

        val replayText = state.selectPinyin("ge")
        assertEquals(listOf("43", "43", "486", "542"), state.segments())
        assertEquals(2, state.activeSegmentIndex)
        assertTrue(state.options().contains("guo"))
        assertEquals("ge'ge'486'542", replayText)
        assertEquals("ge'ge'gun'jia", state.displayText())

        assertEquals("ge'ge'guo'542", state.selectPinyin("guo"))
        assertEquals("ge'ge'guo'jia", state.displayText())
        assertEquals(3, state.activeSegmentIndex)
        assertTrue(state.options().contains("jia"))

        assertEquals("ge'ge'guo'jia", state.selectPinyin("jia"))
        assertEquals(4, state.activeSegmentIndex)
        assertTrue(state.options().isEmpty())
    }
}
