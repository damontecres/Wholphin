package com.github.damontecres.wholphin.playback.leveling.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.tan

/** Constructor-initialized tables: bounded linear interpolation avoids per-frame transcendental calls.
 * log10 max error <1.4e-8; amplitude relative error <1.7e-7 (0.01 dB grid).
 */
internal object DspMath {
    private val logs = DoubleArray(2049) { kotlin.math.log10(1.0 + it / 2048.0) }
    private val amplitudes = DoubleArray(24001) { exp((-160 + it * 0.01) * ln(10.0) / 20) }

    fun log10(x: Double): Double {
        val bits = x.toRawBits()
        val exponent = ((bits ushr 52) and 2047).toInt() - 1023
        if (exponent == -1023) return kotlin.math.log10(x)
        val mantissa = bits and 0x000fffffffffffffL
        val index = (mantissa ushr 41).toInt()
        val fraction = (mantissa and 0x1ffffffffffL).toDouble() / 2199023255552.0
        return exponent * 0.3010299956639812 + logs[index] + fraction * (logs[index + 1] - logs[index])
    }

    fun amplitude(db: Double): Double {
        val position = (db + 160) * 100
        if (position < 0 || position >= 24000) return exp(db * ln(10.0) / 20)
        val i = position.toInt()
        val fraction = position - i
        return amplitudes[i] + fraction * (amplitudes[i + 1] - amplitudes[i])
    }
}
