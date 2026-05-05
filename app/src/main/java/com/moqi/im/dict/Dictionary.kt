package com.moqi.im.dict

interface Dictionary {
    fun lookup(code: String): List<String>
    fun lookupPrefix(prefix: String): List<String>
}