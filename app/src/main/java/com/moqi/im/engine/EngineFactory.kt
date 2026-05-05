package com.moqi.im.engine

import com.moqi.im.dict.DictionaryManager

object EngineFactory {
    fun create(mode: InputMode): InputEngine {
        return when (mode) {
            InputMode.PINYIN -> PinyinEngine().also {
                it.setDictionary(DictionaryManager.getPinyinDictionary())
            }
            InputMode.WUBI -> WubiEngine().also {
                it.setDictionary(DictionaryManager.getWubiDictionary())
            }
            InputMode.ENGLISH -> EnglishEngine().also {
                it.setDictionary(DictionaryManager.getEnglishDictionary())
            }
            InputMode.VOICE -> VoiceEngine()
        }
    }
}