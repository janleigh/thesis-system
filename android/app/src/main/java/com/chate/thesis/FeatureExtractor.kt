package com.chate.thesis

import kotlin.math.pow
import kotlin.math.sqrt

data class KeystrokeEvent(
    val keyCode: Int,
    val eventType: Int, // 0 for DOWN, 1 for UP
    val timestampMs: Double
)

data class FeatureVector(
    val dwellTimes: MutableList<Double> = mutableListOf(),
    val flightTimes: MutableList<Double> = mutableListOf(),
    val ikiTimes: MutableList<Double> = mutableListOf(),
    var dwellMean: Double = 0.0,
    var dwellStd: Double = 0.0,
    var flightMean: Double = 0.0,
    var flightStd: Double = 0.0,
    var ikiMean: Double = 0.0,
    var ikiStd: Double = 0.0,
    var keystrokeCount: Int = 0
) {
    fun toStatArray(): DoubleArray {
        return doubleArrayOf(
            dwellMean, dwellStd,
            flightMean, flightStd,
            ikiMean, ikiStd
        )
    }

    fun isValid(): Boolean {
        return dwellTimes.isNotEmpty() && flightTimes.isNotEmpty() && ikiTimes.isNotEmpty()
    }
}

class FeatureExtractor {

    fun extract(events: List<KeystrokeEvent>): FeatureVector? {
        if (events.isEmpty()) return null

        val sortedEvents = events.sortedBy { it.timestampMs }

        data class PairedKey(val keyCode: Int, val downTs: Double, val upTs: Double)
        val pairedKeys = mutableListOf<PairedKey>()
        
        val pendingDowns = mutableMapOf<Int, ArrayDeque<Double>>()

        for (event in sortedEvents) {
            val key = event.keyCode
            val ts = event.timestampMs
            if (event.eventType == 0) { // DOWN
                pendingDowns.getOrPut(key) { ArrayDeque() }.addLast(ts)
            } else if (event.eventType == 1) { // UP
                val downQueue = pendingDowns[key]
                if (downQueue != null && downQueue.isNotEmpty()) {
                    val downTs = downQueue.removeFirst()
                    pairedKeys.add(PairedKey(key, downTs, ts))
                }
            }
        }

        // Sort by down timestamp to ensure chronological order for consecutive typing features
        pairedKeys.sortBy { it.downTs }

        val fv = FeatureVector()

        // dwell times (filter touch artifacts < 10ms and stuck keys > 500ms)
        for (pk in pairedKeys) {
            val dwell = pk.upTs - pk.downTs
            if (dwell in MIN_DWELL_MS..MAX_DWELL_MS) {
                fv.dwellTimes.add(dwell)
            }
        }

        // flight and IKI times (filter inter-word / reading pauses > 750ms)
        for (i in 0 until pairedKeys.size - 1) {
            val current = pairedKeys[i]
            val next = pairedKeys[i + 1]

            val flight = next.downTs - current.upTs
            if (flight in MIN_FLIGHT_MS..MAX_FLIGHT_MS) {
                fv.flightTimes.add(flight) // Can be legitimately negative for rollover typing
            }

            val iki = next.downTs - current.downTs
            if (iki in 0.0..MAX_IKI_MS) {
                fv.ikiTimes.add(iki)
            }
        }

        fv.keystrokeCount = fv.dwellTimes.size
        computeStats(fv)
        return fv
    }

    companion object {
        const val MIN_DWELL_MS = 10.0
        const val MAX_DWELL_MS = 600.0
        const val MIN_FLIGHT_MS = -300.0
        const val MAX_FLIGHT_MS = 1200.0
        const val MAX_IKI_MS = 1500.0
    }

    private fun computeStats(fv: FeatureVector) {
        fv.dwellMean = safeMean(fv.dwellTimes)
        fv.dwellStd = safeStd(fv.dwellTimes)
        fv.flightMean = safeMean(fv.flightTimes)
        fv.flightStd = safeStd(fv.flightTimes)
        fv.ikiMean = safeMean(fv.ikiTimes)
        fv.ikiStd = safeStd(fv.ikiTimes)
    }

    private fun safeMean(list: List<Double>): Double {
        return if (list.isNotEmpty()) list.average() else 0.0
    }

    private fun safeStd(list: List<Double>): Double {
        if (list.size < 2) return 0.0
        val mean = safeMean(list)
        // population std dev matching Python's pstdev
        val variance = list.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }
}
