package com.github.damontecres.dolphin.ui.main

import androidx.annotation.StringRes
import com.github.damontecres.dolphin.R

/**
 * All possible homesections, "synced" with jellyfin-web.
 *
 * https://github.com/jellyfin/jellyfin-web/blob/master/src/components/homesections/homesections.js
 */
enum class HomeSection(
    val key: String,
    @param:StringRes val nameRes: Int,
) {
    LATEST_MEDIA("latestmedia", R.string.home_section_latest_media),
    LIBRARY_TILES_SMALL("smalllibrarytiles", R.string.home_section_library),
    LIBRARY_BUTTONS("librarybuttons", R.string.home_section_library_small),
    RESUME("resume", R.string.home_section_resume),
    RESUME_AUDIO("resumeaudio", R.string.home_section_resume_audio),
    RESUME_BOOK("resumebook", R.string.home_section_resume_book),
    ACTIVE_RECORDINGS("activerecordings", R.string.home_section_active_recordings),
    NEXT_UP("nextup", R.string.home_section_next_up),
    LIVE_TV("livetv", R.string.home_section_livetv),
    NONE("none", R.string.home_section_none),
    ;

    companion object {
        fun fromKey(key: String): HomeSection = entries.firstOrNull { it.key == key } ?: NONE
    }
}
