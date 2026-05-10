package com.moqi.im.keyboard

class T9PinyinSelectionState {
    private val selectedPinyinBySegment = mutableMapOf<Int, String>()
    private val inferredPinyinBySegment = mutableMapOf<Int, String>()

    private val pinyinDigits = StringBuilder()

    var activeSegmentIndex: Int = 0
        private set

    val digits: String
        get() = pinyinDigits.toString()

    fun segments(): List<String> = T9Pinyin.segmentDigits(digits)

    fun appendDigit(digit: Char) {
        if (digit == '1' && (pinyinDigits.isEmpty() || pinyinDigits.last() == '1')) {
            return
        }
        val previousWasSeparator = pinyinDigits.lastOrNull() == '1'
        pinyinDigits.append(digit)
        val segmentLastIndex = segments().lastIndex.coerceAtLeast(0)
        activeSegmentIndex = when {
            digit == '1' || previousWasSeparator -> segmentLastIndex
            else -> activeSegmentIndex.coerceIn(0, segmentLastIndex)
        }
        pruneSelections()
        if (digit == '1') {
            inferredPinyinBySegment.clear()
        }
    }

    fun deleteLast() {
        if (pinyinDigits.isNotEmpty()) {
            pinyinDigits.deleteAt(pinyinDigits.lastIndex)
        }
        pruneSelections()
    }

    fun selectPinyin(pinyin: String): String {
        if (pinyin.isBlank()) return replayText()
        val currentSegments = segments()
        if (currentSegments.isEmpty()) return replayText()
        val segmentIndex = activeSegmentIndex.coerceIn(0, currentSegments.lastIndex)
        splitSegmentForSelectedPinyin(segmentIndex, pinyin, currentSegments)
        selectedPinyinBySegment[segmentIndex] = pinyin
        val updatedSegments = segments()
        activeSegmentIndex = (segmentIndex + 1).coerceAtMost(updatedSegments.lastIndex)
        pruneSelections()
        return replayText()
    }

    fun options(): List<String> {
        val currentSegments = segments()
        if (currentSegments.isEmpty()) return emptyList()
        activeSegmentIndex = activeSegmentIndex.coerceIn(0, currentSegments.lastIndex)
        return T9Pinyin.optionsFor(currentSegments[activeSegmentIndex])
    }

    fun replayText(): String {
        if (pinyinDigits.isEmpty()) return ""
        return segments().mapIndexed { index, segment ->
            selectedPinyinBySegment[index]
                ?: inferredPinyinBySegment[index]
                ?: T9Pinyin.defaultPinyinFor(segment)
        }.joinToString("'")
    }

    fun reset() {
        pinyinDigits.clear()
        selectedPinyinBySegment.clear()
        inferredPinyinBySegment.clear()
        activeSegmentIndex = 0
    }

    private fun splitSegmentForSelectedPinyin(segmentIndex: Int, pinyin: String, currentSegments: List<String>) {
        val selectedDigits = T9Pinyin.digitsFor(pinyin)
        val segmentDigits = currentSegments.getOrNull(segmentIndex) ?: return
        if (selectedDigits.isBlank() ||
            selectedDigits.length >= segmentDigits.length ||
            !segmentDigits.startsWith(selectedDigits)
        ) {
            return
        }

        val updatedSegments = currentSegments.toMutableList()
        updatedSegments[segmentIndex] = selectedDigits
        updatedSegments.add(segmentIndex + 1, segmentDigits.drop(selectedDigits.length))
        pinyinDigits.clear()
        pinyinDigits.append(updatedSegments.joinToString("1"))

        val oldSelected = selectedPinyinBySegment.toMap()
        selectedPinyinBySegment.clear()
        oldSelected.forEach { (index, selected) ->
            when {
                index < segmentIndex -> selectedPinyinBySegment[index] = selected
                index > segmentIndex -> selectedPinyinBySegment[index + 1] = selected
            }
        }
        inferredPinyinBySegment.clear()
    }

    private fun pruneSelections() {
        val size = segments().size
        selectedPinyinBySegment.keys
            .filter { it >= size }
            .forEach { selectedPinyinBySegment.remove(it) }
        inferredPinyinBySegment.keys
            .filter { it >= size }
            .forEach { inferredPinyinBySegment.remove(it) }
    }
}
