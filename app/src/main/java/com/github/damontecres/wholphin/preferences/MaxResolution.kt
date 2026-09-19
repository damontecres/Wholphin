package com.github.damontecres.wholphin.preferences

enum class MaxResolution(
    val value: Int,
) {
    NO_LIMIT(0),
    ULTRA_HD_8K(4320),
    ULTRA_HD_4K(2160),
    FULL_HD(1080),
    HD(720),
    PAL_SD(576),
    NTSC_SD(480),
    LOW_DEF(360),
    ;

    companion object {
        fun fromValue(value: Int): MaxResolution = entries.firstOrNull { it.value == value } ?: NO_LIMIT
    }
}
