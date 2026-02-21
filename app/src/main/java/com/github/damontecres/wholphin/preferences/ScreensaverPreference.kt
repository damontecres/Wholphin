package com.github.damontecres.wholphin.preferences

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.WholphinApplication
import kotlin.time.Duration.Companion.milliseconds

object ScreensaverPreference {
    val Enabled =
        AppSwitchPreference<AppPreferences>(
            title = R.string.enabled,
            defaultValue = false,
            getter = { it.interfacePreferences.screensaverPreference.enabled },
            setter = { prefs, value ->
                prefs.updateScreensaverPreferences { enabled = value }
            },
            summaryOn = R.string.yes,
            summaryOff = R.string.no,
        )

    const val DEFAULT_START_DELAY = 15 * 60_000L
    private val startDelayValues =
        listOf(
            30_000L,
            60_000L,
            2 * 60_000L,
            5 * 60_000L,
            10 * 60_000L,
            15 * 60_000L,
            30 * 60_000L,
            60 * 60_000L,
        )
    val StartDelay =
        AppSliderPreference<AppPreferences>(
            title = R.string.start_after,
            defaultValue = startDelayValues.indexOf(DEFAULT_START_DELAY).toLong(),
            min = 0,
            max = startDelayValues.size - 1L,
            interval = 1,
            getter = {
                startDelayValues.indexOf(it.interfacePreferences.screensaverPreference.startDelay).toLong()
            },
            setter = { prefs, value ->
                prefs.updateScreensaverPreferences {
                    startDelay = startDelayValues[value.toInt()]
                }
            },
            summarizer = { value ->
                if (value != null) {
                    val v = startDelayValues.getOrNull(value.toInt()) ?: DEFAULT_START_DELAY
                    v.milliseconds.toString()
                } else {
                    null
                }
            },
        )

    const val DEFAULT_DURATION = 30_000L
    private val durationValues =
        listOf(
            15_000L,
            30_000L,
            60_000L,
            2 * 60_000L,
        )
    val Duration =
        AppSliderPreference<AppPreferences>(
            title = R.string.duration,
            defaultValue = durationValues.indexOf(DEFAULT_DURATION).toLong(),
            min = 0,
            max = durationValues.size - 1L,
            interval = 1,
            getter = {
                durationValues.indexOf(it.interfacePreferences.screensaverPreference.duration).toLong()
            },
            setter = { prefs, value ->
                prefs.updateScreensaverPreferences {
                    duration = durationValues[value.toInt()]
                }
            },
            summarizer = { value ->
                if (value != null) {
                    val v = durationValues.getOrNull(value.toInt()) ?: DEFAULT_DURATION
                    v.milliseconds.toString()
                } else {
                    null
                }
            },
        )

    val Animate =
        AppSwitchPreference<AppPreferences>(
            title = R.string.animate,
            defaultValue = true,
            getter = { it.interfacePreferences.screensaverPreference.animate },
            setter = { prefs, value ->
                prefs.updateScreensaverPreferences { animate = value }
            },
            summaryOn = R.string.enabled,
            summaryOff = R.string.disabled,
        )

    const val DEFAULT_MAX_AGE = 16
    private val maxAgeValues = listOf(0, 5, 10, 13, 14, 16, 18, 21, -1)
    val MaxAge =
        AppSliderPreference<AppPreferences>(
            title = R.string.max_age_rating,
            defaultValue = maxAgeValues.indexOf(DEFAULT_MAX_AGE).toLong(),
            min = 0,
            max = maxAgeValues.size - 1L,
            interval = 1,
            getter = {
                it.interfacePreferences.screensaverPreference.maxAgeFilter
                    .takeIf { it >= 0 }
                    ?.let { maxAgeValues.indexOf(it).toLong() }
                    ?: maxAgeValues.lastIndex.toLong()
            },
            setter = { prefs, value ->
                prefs.updateScreensaverPreferences {
                    maxAgeFilter = maxAgeValues[value.toInt()]
                }
            },
            summarizer = { value ->
                when (value) {
                    null -> {
                        null
                    }

                    maxAgeValues.lastIndex.toLong() -> {
                        WholphinApplication.instance.getString(R.string.no_max)
                    }

                    0L -> {
                        WholphinApplication.instance.getString(R.string.for_all_ages)
                    }

                    else -> {
                        WholphinApplication.instance.getString(
                            R.string.up_to_age,
                            maxAgeValues[value.toInt()].toString(),
                        )
                    }
                }
            },
        )

    val Start =
        AppClickablePreference<AppPreferences>(
            title = R.string.start_screensaver,
        )
}
