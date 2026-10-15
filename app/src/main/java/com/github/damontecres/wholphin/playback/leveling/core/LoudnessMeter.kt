package com.github.damontecres.wholphin.playback.leveling.core

import kotlin.math.*

/** De Man's sample-rate-derived form of the BS.1770 shelf and RLB filters. */
internal class Detector(val rate: Int, val channels: Int) {
    private val weights = DoubleArray(channels) { c ->
        if(channels == 6 || channels == 8) when { c==3 -> 0.0; c>=4 -> 1.41; else -> 1.0 } else 1.0
    }
    val sanitizedFrame=FloatArray(channels)
    var peak=0.0; private set
    private val s1=DoubleArray(channels); private val s2=DoubleArray(channels)
    private val h1=DoubleArray(channels); private val h2=DoubleArray(channels)
    private val shelfB0: Double
    private val shelfB1: Double
    private val shelfB2: Double
    private val shelfA1: Double
    private val shelfA2: Double
    private val highA1: Double
    private val highA2: Double
    private val blocks=DoubleArray(30)
    private var count=0; private var blockIndex=0; private var blockCount=0
    private var blockLength=rate/10
    private var energy=0.0
    var blockCompleted=false; private set
    var momentary=Double.NEGATIVE_INFINITY; private set
    var shortTerm=Double.NEGATIVE_INFINITY; private set
    var momentaryPower=0.0; private set
    val completeMomentary: Boolean get()=blockCount>=4
    init {
        checkFormat(rate,channels)
        DspMath.amplitude(0.0) // Initialize shared lookup tables before the audio callback.
        val k=tan(PI*1681.974450955533/rate)
        val vh=10.0.pow(3.999843853973347/20)
        val vb=vh.pow(0.4996667741545416)
        val q=0.7071752369554196; val a=1+k/q+k*k
        shelfB0=(vh+vb*k/q+k*k)/a; shelfB1=2*(k*k-vh)/a; shelfB2=(vh-vb*k/q+k*k)/a
        shelfA1=2*(k*k-1)/a; shelfA2=(1-k/q+k*k)/a
        val kh=tan(PI*38.13547087602444/rate); val qh=0.5003270373238773
        val ah=1+kh/qh+kh*kh
        // RLB numerator is deliberately [1,-2,1], not a unity-high-frequency normalized high-pass.
        highA1=2*(kh*kh-1)/ah; highA2=(1-kh/qh+kh*kh)/ah
    }
    fun frame(input: FloatArray, offset: Int): Double {
        var power=0.0; var c=0; peak=0.0
        while(c<channels) {
            val v=input[offset+c]; val clean=if(v.isFinite()) v else 0f
            sanitizedFrame[c]=clean; val x=clean.toDouble(); peak=max(peak,abs(x))
            if(weights[c] == 0.0) { c++; continue } // LFE participates in peak safety, never K loudness.
            val y=shelfB0*x+s1[c]
            s1[c]=shelfB1*x-shelfA1*y+s2[c]; s2[c]=shelfB2*x-shelfA2*y
            val z=y+h1[c]
            h1[c]=-2.0*y-highA1*z+h2[c]; h2[c]=y-highA2*z
            power += weights[c]*z*z; c++
        }
        energy += power; count++; blockCompleted=false
        // Rational boundaries avoid drift at rates not divisible by ten.
        if(count == blockLength) completeBlock()
        return power
    }
    // Keep 100 ms window/denormal bookkeeping out of the hot per-frame method.
    private fun completeBlock() {
        blocks[blockIndex%30]=energy/count
        blockIndex=(blockIndex+1)%30; blockCount=min(30,blockCount+1)
        blockLength=((blockIndex.toLong()+1)*rate/10-blockIndex.toLong()*rate/10).toInt()
        var m=0.0; var s=0.0; var i=0
        while(i<blockCount) {
            val p=blocks[(blockIndex+29-i)%30]; s+=p; if(i<4) m+=p; i++
        }
        // Unfilled windows contain silence, matching EBU startup semantics.
        momentaryPower=m/4; momentary=loudness(momentaryPower); shortTerm=loudness(s/30)
        var c=0
        while(c<channels) {
            if(abs(s1[c])+abs(s2[c])+abs(h1[c])+abs(h2[c]) < 1e-30) {
                s1[c]=0.0; s2[c]=0.0; h1[c]=0.0; h2[c]=0.0
            }
            c++
        }
        energy=0.0; count=0; blockCompleted=true
    }
    fun clear() {
        s1.fill(0.0); s2.fill(0.0); h1.fill(0.0); h2.fill(0.0); blocks.fill(0.0)
        count=0; blockIndex=0; blockCount=0; blockLength=rate/10; energy=0.0; momentaryPower=0.0
        momentary=Double.NEGATIVE_INFINITY; shortTerm=Double.NEGATIVE_INFINITY; blockCompleted=false
    }
}

/** BS.1770 400 ms blocks, 75% overlap, -70 LUFS absolute and -10 LU relative gates.
 * Single-thread owner. Exact block energies are preallocated for 24 hours by default.
 * Reserve longer history before processing (or while paused), never in the audio callback.
 * Reading integratedLufs scans history and belongs off the audio callback.
 */
class LoudnessMeter(val sampleRate: Int, val channelCount: Int, historySeconds: Int) {
    constructor(sampleRate: Int, channelCount: Int): this(sampleRate,channelCount,86400)
    private val detector=Detector(sampleRate,channelCount)
    private var history=DoubleArray(historyBlocks(historySeconds))
    private var historyCount=0
    private var absoluteSum=0.0
    private val absoluteGatePower=10.0.pow((-70.0+0.691)/10)
    /** An exhausted history invalidates integrated loudness, but M/S continue without allocation. */
    var historyOverflowed=false; private set
    val historyCapacitySeconds: Int get()=history.size/10

    /** Owner-only, outside process and never concurrently with it. Cannot recover already lost blocks. */
    fun reserveHistory(seconds: Int) {
        val blocks=historyBlocks(seconds)
        if(blocks>history.size) history=history.copyOf(blocks)
    }
    private fun historyBlocks(seconds: Int): Int {
        require(seconds in 1..Int.MAX_VALUE/10)
        return seconds*10
    }
    fun process(input: FloatArray, offset: Int, frames: Int) {
        checkBuffer(input,offset,frames,channelCount)
        var i=offset; val end=offset+frames*channelCount
        while(i<end) {
            detector.frame(input,i); i+=channelCount
            if(detector.blockCompleted && detector.completeMomentary && detector.momentaryPower>absoluteGatePower) {
                if(historyCount<history.size) {
                    val power=detector.momentaryPower
                    history[historyCount++]=power; absoluteSum+=power
                } else historyOverflowed=true
            }
        }
    }
    val momentaryLufs: Float get()=detector.momentary.toFloat()
    val shortTermLufs: Float get()=detector.shortTerm.toFloat()
    val integratedLufs: Float get() {
        check(!historyOverflowed) { "Integrated history exhausted; reserveHistory before capacity is reached" }
        if(historyCount==0) return Float.NEGATIVE_INFINITY
        // Compare actual powers, not rounded logarithms or histogram bins: near-gate populations matter.
        val threshold=max(absoluteGatePower,0.1*(absoluteSum/historyCount))
        var energy=0.0; var n=0; var i=0
        while(i<historyCount) {
            val power=history[i++]
            if(power>threshold) { energy+=power; n++ }
        }
        return if(n==0) Float.NEGATIVE_INFINITY else loudness(energy/n).toFloat()
    }
}
