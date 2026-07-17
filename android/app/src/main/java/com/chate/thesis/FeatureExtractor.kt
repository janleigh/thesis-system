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

        // sort by timestamp just to be safe
        val sortedEvents = events.sortedBy { it.timestampMs }

        val downTimes = mutableMapOf<Int, Double>()
        val upTimes = mutableMapOf<Int, Double>()
        val keyOrder = mutableListOf<Int>()

        for (event in sortedEvents) {
            val key = event.keyCode
            val ts = event.timestampMs
            if (event.eventType == 0) { // DOWN
                if (!downTimes.containsKey(key)) {
                    keyOrder.add(key)
                }
                downTimes[key] = ts
            } else if (event.eventType == 1) { // UP
                upTimes[key] = ts
            }
        }

        val fv = FeatureVector()

        // how long each key is held down
        for (key in keyOrder) {
            if (downTimes.containsKey(key) && upTimes.containsKey(key)) {
                val dwell = upTimes[key]!! - downTimes[key]!!
                if (dwell >= 0) {
                    fv.dwellTimes.add(dwell)
                }
            }
        }

        // how long between consecutive keypresses
        for (i in 0 until keyOrder.size - 1) {
            val keyN = keyOrder[i]
            val keyN1 = keyOrder[i + 1]

            if (upTimes.containsKey(keyN) && downTimes.containsKey(keyN1)) {
                val flight = downTimes[keyN1]!! - upTimes[keyN]!!
                fv.flightTimes.add(flight)
            }

            if (downTimes.containsKey(keyN) && downTimes.containsKey(keyN1)) {
                val iki = downTimes[keyN1]!! - downTimes[keyN]!!
                if (iki >= 0) {
                    fv.ikiTimes.add(iki)
                }
            }
        }

        fv.keystrokeCount = fv.dwellTimes.size
        computeStats(fv)
        return fv
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
