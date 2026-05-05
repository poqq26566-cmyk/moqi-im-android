package com.moqi.im.engine

import com.moqi.im.dict.Dictionary

class PinyinEngine : InputEngine {

    private lateinit var dictionary: Dictionary
    private val pinyinSyllables = setOf(
        "a", "ai", "an", "ang", "ao",
        "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi", "bian", "biao", "bie", "bin", "bing", "bo", "bu",
        "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng", "cha", "chai", "chan", "chang", "chao", "che", "chen", "cheng", "chi", "chong", "chou", "chu", "chua", "chuai", "chuan", "chuang", "chui", "chun", "chuo", "ci", "cong", "cou", "cu", "cuan", "cui", "cun", "cuo",
        "da", "dai", "dan", "dang", "dao", "de", "dei", "den", "deng", "di", "dian", "diao", "die", "ding", "diu", "dong", "dou", "du", "duan", "dui", "dun", "duo",
        "e", "ei", "en", "er",
        "fa", "fan", "fang", "fei", "fen", "feng", "fo", "fou", "fu",
        "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng", "gong", "gou", "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
        "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng", "hong", "hou", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
        "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing", "jiong", "jiu", "ju", "juan", "jue", "jun",
        "ka", "kai", "kan", "kang", "kao", "ke", "ken", "keng", "kong", "kou", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
        "la", "lai", "lan", "lang", "lao", "le", "lei", "leng", "li", "lia", "lian", "liang", "liao", "lie", "lin", "ling", "liu", "lo", "long", "lou", "lu", "luan", "lun", "luo", "lv", "lve",
        "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng", "mi", "mian", "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu",
        "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng", "ni", "nian", "niang", "niao", "nie", "nin", "ning", "niu", "nong", "nou", "nu", "nuan", "nun", "nuo", "nv", "nve",
        "o", "ou",
        "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng", "pi", "pian", "piao", "pie", "pin", "ping", "po", "pou", "pu",
        "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing", "qiong", "qiu", "qu", "quan", "que", "qun",
        "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong", "rou", "ru", "rua", "ruan", "rui", "run", "ruo",
        "sa", "sai", "san", "sang", "sao", "se", "sen", "seng", "sha", "shai", "shan", "shang", "shao", "she", "shei", "shen", "sheng", "shi", "shou", "shu", "shua", "shuai", "shuan", "shuang", "shui", "shun", "shuo", "si", "song", "sou", "su", "suan", "sui", "sun", "suo",
        "ta", "tai", "tan", "tang", "tao", "te", "teng", "ti", "tian", "tiao", "tie", "ting", "tong", "tou", "tu", "tuan", "tui", "tun", "tuo",
        "wa", "wai", "wan", "wang", "wei", "wen", "weng", "wo", "wu",
        "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiong", "xiu", "xu", "xuan", "xue", "xun",
        "ya", "yan", "yang", "yao", "ye", "yi", "yin", "ying", "yo", "yong", "you", "yu", "yuan", "yue", "yun",
        "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng", "zha", "zhai", "zhan", "zhang", "zhao", "zhe", "zhei", "zhen", "zheng", "zhi", "zhong", "zhou", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhui", "zhun", "zhuo", "zi", "zong", "zou", "zu", "zuan", "zui", "zun", "zuo"
    )

    override fun processInput(input: String): List<String> {
        if (input.isBlank()) return emptyList()

        val segmented = segmentPinyin(input.lowercase())
        val candidates = mutableListOf<String>()

        for (pinyin in segmented) {
            val words = dictionary.lookup(pinyin)
            candidates.addAll(words)
        }

        if (candidates.isEmpty() && input.all { it.isLetter() }) {
            candidates.addAll(dictionary.lookup(input.lowercase()))
        }

        return candidates.distinct().take(20)
    }

    override fun reset() {}

    override fun setDictionary(dict: Dictionary) {
        dictionary = dict
    }

    private fun segmentPinyin(input: String): List<String> {
        if (input.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        var pos = 0

        while (pos < input.length) {
            var matched = false
            for (len in minOf(6, input.length - pos) downTo 1) {
                val sub = input.substring(pos, pos + len)
                if (pinyinSyllables.contains(sub)) {
                    result.add(sub)
                    pos += len
                    matched = true
                    break
                }
            }
            if (!matched) {
                if (result.isNotEmpty() && pos < input.length) {
                    result[result.size - 1] = result.last() + input[pos]
                } else {
                    result.add(input[pos].toString())
                }
                pos++
            }
        }

        return result
    }

    private val t9Map = mapOf(
        '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
        '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz"
    )

    fun processT9Input(input: String): List<String> {
        if (input.isEmpty()) return emptyList()

        val candidates = mutableListOf<String>()
        val expandedPatterns = expandT9(input, 0)
        for (pattern in expandedPatterns) {
            val segmented = segmentPinyin(pattern)
            val pinyinStr = segmented.joinToString("")
            val results = dictionary.lookup(pinyinStr)
            if (results.isNotEmpty()) {
                candidates.addAll(results)
            }
            for (seg in segmented) {
                val segResults = dictionary.lookup(seg)
                if (segResults.isNotEmpty()) {
                    candidates.addAll(segResults)
                }
            }
        }

        return candidates.distinct().take(20)
    }

    private fun expandT9(input: String, pos: Int): List<String> {
        if (pos >= input.length) return listOf("")
        val digit = input[pos]
        val letters = t9Map[digit] ?: return listOf(digit + expandT9(input, pos + 1).firstOrNull().orEmpty())
        val results = mutableListOf<String>()
        for (ch in letters) {
            for (suffix in expandT9(input, pos + 1)) {
                results.add(ch + suffix)
            }
            if (results.size > 500) break
        }
        return if (results.isEmpty()) listOf("") else results.take(500)
    }
}