package com.github.damontecres.wholphin.preferences

enum class MaxAvcLevel(
    val value: Int,
) {
    AUTO(0),
    LEVEL_5_2(52),
    LEVEL_5_1(51),
    LEVEL_5_0(50),
    LEVEL_4_2(42),
    LEVEL_4_1(41),
    LEVEL_4_0(40),
    ;

    companion object {
        fun fromValue(value: Int): MaxAvcLevel = entries.firstOrNull { it.value == value } ?: AUTO
    }
}
