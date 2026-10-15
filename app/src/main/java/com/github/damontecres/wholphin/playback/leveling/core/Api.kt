package com.github.damontecres.wholphin.playback.leveling.core

import kotlin.math.*

enum class LevelingMode { OFF, LIGHT, STRONG }

data class LevelingProfile(
    val targetLufs: Float, val maxCutDb: Float, val maxBoostDb: Float,
    val warmTauSeconds: Float, val slowTauSeconds: Float,
    val compressorThresholdOffsetLu: Float, val compressorRatio: Float, val compressorKneeDb: Float,
    val compressorAttackMs: Float, val compressorReleaseMs: Float,
    val compressorMaxReductionDb: Float, val limiterCeilingDbfs: Float,
) {
    companion object {
        val LIGHT = LevelingProfile(-26f,6f,6f,3f,20f,1.25f,4f,6f,20f,200f,8f,-4f)
        val STRONG = LevelingProfile(-26f,10f,9f,2f,10f,0f,4f,6f,10f,300f,14f,-4f)
        fun of(mode: LevelingMode): LevelingProfile? = when(mode) {
            LevelingMode.OFF -> null; LevelingMode.LIGHT -> LIGHT; LevelingMode.STRONG -> STRONG
        }
    }
}

internal fun loudness(power: Double): Double = if (power > 0.0) -0.691 + 10.0 * DspMath.log10(power) else Double.NEGATIVE_INFINITY
internal fun amplitude(db: Double): Double = DspMath.amplitude(db)
internal fun checkFormat(rate: Int, channels: Int) { require(rate in 8000..384000); require(channels in 1..32) }
internal fun checkBuffer(a: FloatArray, offset: Int, frames: Int, channels: Int) {
    require(offset >= 0 && frames >= 0 && offset.toLong() + frames.toLong()*channels <= a.size)
}

/** Single audio-thread owner. Only mode is shared; lifecycle calls belong on the audio thread. */
class VolumeLeveler(val sampleRate: Int, val channelCount: Int) {
    init { checkFormat(sampleRate, channelCount) }
    val latencyFrames: Int = (0.004*sampleRate).roundToInt()
    @Volatile var mode: LevelingMode = LevelingMode.OFF
    private val detector = Detector(sampleRate, channelCount)
    private val light = Branch(sampleRate, latencyFrames, LevelingProfile.LIGHT)
    private val strong = Branch(sampleRate, latencyFrames, LevelingProfile.STRONG)
    private val delay = FloatArray(latencyFrames * channelCount)
    private val peakDelay = DoubleArray(latencyFrames)
    private var peakCursor = 0
    private var cursor = 0
    private var lightMix = 0.0
    private var strongMix = 0.0
    private var lightStep = 0.0
    private var strongStep = 0.0
    private var transitionLeft = 0
    private var observedMode = LevelingMode.OFF
    private val fadeFrames = (sampleRate * 0.1).roundToInt()
    private var clamps = 0L
    private var started = false
    val levelEstimateLufs: Float get() = selected().estimate.toFloat()
    val levelerGainDb: Float get() = selected().gainDb.toFloat()
    val compressorGainDb: Float get() = -selected().reduction.toFloat()
    val limiterGainDb: Float get() = (20*log10(selected().limiterGain)).toFloat()
    val clampedSampleCount: Long get() = clamps
    val maximumLevelerBoostDb: Float get() = max(selected().maximumGainDb,selected().gainDb).toFloat()
    private fun selected(): Branch = if (mode == LevelingMode.LIGHT) light else strong

    fun process(input: FloatArray, inputOffset: Int, output: FloatArray, outputOffset: Int, frames: Int) {
        checkBuffer(input,inputOffset,frames,channelCount); checkBuffer(output,outputOffset,frames,channelCount)
        // In-place and output-before-input overlap are safe. Forward overlap would overwrite future input.
        require(input !== output || outputOffset <= inputOffset || outputOffset.toLong() >= inputOffset.toLong()+frames.toLong()*channelCount)
        var src = inputOffset; var dst = outputOffset
        var f = 0
        while (f < frames) {
            val requested = mode
            if(!started) {
                observedMode=requested
                lightMix=if(requested == LevelingMode.LIGHT) 1.0 else 0.0
                strongMix=if(requested == LevelingMode.STRONG) 1.0 else 0.0
                started=true
            }
            if (requested != observedMode) {
                observedMode = requested
                transitionLeft = fadeFrames
                lightStep = ((if(requested == LevelingMode.LIGHT) 1.0 else 0.0)-lightMix)/fadeFrames
                strongStep = ((if(requested == LevelingMode.STRONG) 1.0 else 0.0)-strongMix)/fadeFrames
            }
            val power = detector.frame(input,src)
            val peak = detector.peak
            if(detector.blockCompleted) {
                light.block(detector.momentary); strong.block(detector.momentary)
            }
            val lg = if(lightMix > 0 || requested == LevelingMode.LIGHT) light.frame(power,peak)
                else { light.advanceInactive(power,peak); 0.0 }
            val sg = if(strongMix > 0 || requested == LevelingMode.STRONG) strong.frame(power,peak)
                else { strong.advanceInactive(power,peak); 0.0 }
            if(transitionLeft > 0) {
                lightMix += lightStep; strongMix += strongStep
                transitionLeft--
                if(transitionLeft == 0) {
                    lightMix = if(observedMode == LevelingMode.LIGHT) 1.0 else 0.0
                    strongMix = if(observedMode == LevelingMode.STRONG) 1.0 else 0.0
                }
            }
            val wet = lightMix*lg + strongMix*sg
            val dry = 1.0-lightMix-strongMix
            val outputGain=dry+wet
            val oldPeak=peakDelay[peakCursor]
            peakDelay[peakCursor]=peak; peakCursor++; if(peakCursor==latencyFrames) peakCursor=0
            var c=0
            if(lightMix == 0.0 && strongMix == 0.0) {
                // Preserve all finite Float bits, including signed zero, on settled Off.
                while(c<channelCount) {
                    output[dst+c]=delay[cursor+c]
                    delay[cursor+c]=detector.sanitizedFrame[c]; c++
                }
            } else if(dry != 0.0 || oldPeak*abs(outputGain) <= light.ceiling) {
                // One linked peak check per frame keeps the ordinary channel loop branch-free.
                while(c<channelCount) {
                    output[dst+c]=(delay[cursor+c]*outputGain).toFloat()
                    delay[cursor+c]=detector.sanitizedFrame[c]; c++
                }
            } else {
                // A dry transition can legitimately exceed the wet ceiling. Settled wet is bounded.
                while(c<channelCount) {
                    var y=delay[cursor+c]*outputGain
                    if(abs(y)>light.ceiling) { clamps++; y=y.coerceIn(-light.ceiling,light.ceiling) }
                    output[dst+c]=y.toFloat()
                    delay[cursor+c]=detector.sanitizedFrame[c]; c++
                }
            }
            cursor += channelCount; if(cursor == delay.size) cursor = 0
            src += channelCount; dst += channelCount; f++
        }
    }
    fun flush() {
        delay.fill(0f); peakDelay.fill(0.0); peakCursor=0; cursor=0; detector.clear(); light.clearFast(); strong.clearFast()
    }
    fun reset() {
        flush(); light.reset(); strong.reset(); clamps=0
        started=false; observedMode=mode; lightMix=if(mode == LevelingMode.LIGHT) 1.0 else 0.0
        strongMix=if(mode == LevelingMode.STRONG) 1.0 else 0.0; transitionLeft=0
    }
}

internal class Branch(rate: Int, private val delay: Int, val profile: LevelingProfile) {
    val ceiling = amplitude(profile.limiterCeilingDbfs.toDouble())
    var estimate = profile.targetLufs.toDouble(); private set
    var gainDb = 0.0; private set
    var reduction = 0.0; private set
    var maximumGainDb = 0.0; private set
    var limiterGain = 1.0; private set
    private val blockFrames = rate / 10
    private val threshold = (profile.targetLufs+profile.compressorThresholdOffsetLu).toDouble()
    private val knee = profile.compressorKneeDb.toDouble()
    private val halfKnee = knee/2
    private val curveSlope = 1.0-1.0/profile.compressorRatio
    private val kneeSlope = curveSlope/(2*knee)
    private val maxReduction = profile.compressorMaxReductionDb.toDouble()
    // Conservative margins exceed the loudness lookup error, so these returns match the full curve bit-for-bit.
    private val lowerKneePower = 10.0.pow((threshold-halfKnee-0.000001+0.691)/10)
    private val saturatedPower = 10.0.pow((threshold+max(halfKnee,maxReduction/curveSlope)+0.000001+0.691)/10)
    private val warmAlpha = -expm1(-0.1/profile.warmTauSeconds)
    private val slowAlpha = -expm1(-0.1/profile.slowTauSeconds)
    private val rmsAlpha = -expm1(-1.0/(rate*0.05))
    private val attackAlpha = -expm1(-1000.0/(rate*profile.compressorAttackMs))
    private val releaseAlpha = -expm1(-1000.0/(rate*profile.compressorReleaseMs))
    private val limitRelease = -expm1(-1.0/(rate*0.080))
    private var activeBlocks = 0
    private var gainStep = 0.0
    private var linearLevelGain = 1.0
    private var linearGainStep = 1.0
    private var compressionGain = 1.0
    private var rampLeft = 0
    private var rms = 0.0
    private var sequence = 0L
    private val gains = DoubleArray(delay)
    private var gainCursor = 0
    private val needs = DoubleArray(delay) { 1.0 }
    private val required = DoubleArray(delay+2)
    private val times = LongArray(delay+2)
    private var head=0; private var tail=0
    fun block(momentary: Double) {
        maximumGainDb=max(maximumGainDb,gainDb)
        if(rms < 1e-30) rms=0.0
        if(reduction < 1e-20) reduction=0.0
        compressionGain=exp(-reduction*ln(10.0)/20)
        if(1.0-limiterGain < 1e-12) limiterGain=1.0
        if(momentary > -50 && momentary > estimate-15) {
            estimate += (if(activeBlocks < 150) warmAlpha else slowAlpha)*(min(momentary,estimate+2.5)-estimate)
            activeBlocks++
        }
        val target = (profile.targetLufs-estimate).coerceIn(-profile.maxCutDb.toDouble(),profile.maxBoostDb.toDouble())
        gainStep=(target-gainDb)/blockFrames; rampLeft=blockFrames
        linearLevelGain=exp(gainDb*ln(10.0)/20)
        linearGainStep=exp(gainStep*ln(10.0)/20)
        // At a pinned gain, repeated additions/multiplies can be exact no-ops (including rounding residue).
        if(linearGainStep==1.0 && gainDb+gainStep==gainDb) rampLeft=0
    }
    private fun advanceLevel(power: Double): Double {
        if(rampLeft > 0) { gainDb += gainStep; linearLevelGain *= linearGainStep; rampLeft-- }
        val levelGain = linearLevelGain
        rms += rmsAlpha*(power*levelGain*levelGain-rms)
        return levelGain
    }
    fun advanceInactive(power: Double, peak: Double) {
        // Keep the full gain/limiter history live. Holding old reduction revives stale ducking on re-enable.
        frame(power,peak)
    }
    fun frame(power: Double, peak: Double): Double {
        val levelGain=advanceLevel(power)
        val desired=desiredReduction(rms)
        val delta=(if(desired > reduction) attackAlpha else releaseAlpha)*(desired-reduction)
        reduction += delta
        // Delta is tiny at audio rate. A cubic exp step is re-anchored exactly every block.
        val x=-delta*(ln(10.0)/20)
        compressionGain=min(1.0,compressionGain*(1+x*(1+x*(0.5+x/6))))
        val gain = levelGain*compressionGain
        val need = if(peak*gain > ceiling) (ceiling/(peak*gain))*(1.0-1e-12) else 1.0
        if(need < 1.0 || limiterGain < 1.0) anticipate(need)
        else { head=0; tail=0; limiterGain=1.0 }
        val delayedGain=gains[gainCursor]
        // The outgoing sample's own requirement is an exact safety bound, including release.
        limiterGain=min(limiterGain,needs[gainCursor])
        gains[gainCursor]=gain; needs[gainCursor]=need
        gainCursor++; if(gainCursor == delay) gainCursor=0
        sequence++
        return delayedGain*limiterGain
    }
    // Keep queue traversal off the common per-frame path when no limiting is needed.
    private fun anticipate(need: Double) {
        val n = required.size
        while(head != tail && times[head] < sequence-delay) head=if(head+1==n) 0 else head+1
        while(head != tail) {
            val prev=if(tail==0) n-1 else tail-1
            if(required[prev] < need) break
            tail=prev
        }
        required[tail]=need; times[tail]=sequence; tail=if(tail+1==n) 0 else tail+1
        val minimum=required[head]
        // Linear anticipation reaches the most restrictive future peak on its due frame.
        val distance=(times[head]-(sequence-delay)).toInt()
        if(minimum < limiterGain) limiterGain += (minimum-limiterGain)/max(1,distance+1)
        else limiterGain += limitRelease*(1.0-limiterGain)
    }
    internal fun desiredReduction(power: Double): Double {
        if(power<=lowerKneePower) return 0.0
        if(power>=saturatedPower) return maxReduction
        val over = loudness(power)-threshold
        return (when {
            over <= -halfKnee -> 0.0
            over >= halfKnee -> curveSlope*over
            else -> kneeSlope*(over+halfKnee)*(over+halfKnee)
        }).coerceAtMost(maxReduction)
    }
    fun clearFast() {
        gainStep=0.0; rampLeft=0; linearLevelGain=exp(gainDb*ln(10.0)/20); linearGainStep=1.0
        rms=0.0; reduction=0.0; compressionGain=1.0; limiterGain=1.0
        sequence=0; gainCursor=0; gains.fill(0.0); needs.fill(1.0); head=0; tail=0
    }
    fun reset() { clearFast(); estimate=profile.targetLufs.toDouble(); gainDb=0.0; linearLevelGain=1.0; maximumGainDb=0.0; activeBlocks=0 }
}
