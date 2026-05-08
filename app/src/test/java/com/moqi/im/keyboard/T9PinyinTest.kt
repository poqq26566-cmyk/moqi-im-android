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
    fun digitsFor_selectedShortPinyinConsumesOnlyItsPrefix() {
        assertEquals("92", T9Pinyin.digitsFor("wa"))
        assertTrue("926".startsWith(T9Pinyin.digitsFor("wa")))
        assertEquals("6", "926".drop(T9Pinyin.digitsFor("wa").length))
    }
}
