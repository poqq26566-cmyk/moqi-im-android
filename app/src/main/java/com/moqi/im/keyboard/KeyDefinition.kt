package com.moqi.im.keyboard

data class KeyDefinition(
    val label: String,
    val keyCode: Int,
    val widthFactor: Float = 1f,
    val isRepeatable: Boolean = false,
    val isSticky: Boolean = false,
    val subLabel: String? = null
)