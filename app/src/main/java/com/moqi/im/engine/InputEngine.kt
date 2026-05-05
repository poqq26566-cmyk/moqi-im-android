package com.moqi.im.engine

enum class InputMode {
    PINYIN, WUBI, ENGLISH, VOICE
}

interface InputEngine {
    fun processInput(input: String): List<String>
    fun reset()
    fun setDictionary(dict: com.moqi.im.dict.Dictionary)
}