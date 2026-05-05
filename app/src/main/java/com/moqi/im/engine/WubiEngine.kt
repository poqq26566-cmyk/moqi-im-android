package com.moqi.im.engine

import com.moqi.im.dict.Dictionary

class WubiEngine : InputEngine {

    private lateinit var dictionary: Dictionary

    private val wubiRegions = mapOf(
        'g' to "一", 'f' to "地", 'd' to "在", 's' to "要", 'a' to "工",
        'h' to "上", 'j' to "是", 'k' to "中", 'l' to "国", 'm' to "同",
        't' to "和", 'r' to "的", 'e' to "有", 'w' to "人", 'q' to "我",
        'y' to "主", 'u' to "产", 'i' to "不", 'o' to "为", 'p' to "这",
        'n' to "民", 'b' to "了", 'v' to "发", 'c' to "以", 'x' to "经"
    )

    override fun processInput(input: String): List<String> {
        if (input.isBlank()) return emptyList()

        val candidates = dictionary.lookup(input.lowercase())

        if (candidates.isEmpty() && input.length == 1 && wubiRegions.containsKey(input[0])) {
            return listOf(wubiRegions[input[0]]!!)
        }

        return candidates.distinct().take(20)
    }

    override fun reset() {}

    override fun setDictionary(dict: Dictionary) {
        dictionary = dict
    }
}