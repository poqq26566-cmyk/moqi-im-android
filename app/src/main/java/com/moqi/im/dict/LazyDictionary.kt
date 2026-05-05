package com.moqi.im.dict

class LazyDictionary(private val loader: () -> Dictionary) : Dictionary {

    private val delegate: Dictionary by lazy { loader() }

    override fun lookup(code: String): List<String> = delegate.lookup(code)
    override fun lookupPrefix(prefix: String): List<String> = delegate.lookupPrefix(prefix)
}