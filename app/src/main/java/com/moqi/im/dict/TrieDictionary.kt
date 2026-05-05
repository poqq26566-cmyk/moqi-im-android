package com.moqi.im.dict

class TrieDictionary : Dictionary {

    private data class TrieNode(
        val children: MutableMap<Char, TrieNode> = mutableMapOf(),
        val words: MutableList<String> = mutableListOf()
    )

    private val root = TrieNode()

    override fun lookup(code: String): List<String> {
        var node = root
        for (ch in code) {
            node = node.children[ch] ?: return emptyList()
        }
        return node.words.toList()
    }

    override fun lookupPrefix(prefix: String): List<String> {
        var node = root
        for (ch in prefix) {
            node = node.children[ch] ?: return emptyList()
        }
        return collectAllWords(node)
    }

    fun insert(code: String, word: String) {
        var node = root
        for (ch in code) {
            node = node.children.getOrPut(ch) { TrieNode() }
        }
        if (word !in node.words) {
            node.words.add(word)
        }
    }

    private fun collectAllWords(node: TrieNode): List<String> {
        val result = mutableListOf<String>()
        result.addAll(node.words)
        for (child in node.children.values) {
            result.addAll(collectAllWords(child))
        }
        return result
    }
}