package com.moqi.im.engine

import com.moqi.im.dict.Dictionary

class EnglishEngine : InputEngine {

    private lateinit var dictionary: Dictionary

    override fun processInput(input: String): List<String> {
        if (input.isBlank()) return emptyList()

        val lower = input.lowercase()
        val candidates = dictionary.lookupPrefix(lower)
        if (candidates.isNotEmpty()) return candidates.take(20)

        val exact = dictionary.lookup(lower)
        return exact.take(20)
    }

    override fun reset() {}

    override fun setDictionary(dict: Dictionary) {
        dictionary = dict
    }
}