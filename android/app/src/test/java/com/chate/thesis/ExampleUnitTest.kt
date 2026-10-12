package com.chate.thesis

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testPauseFiltering() {
        val extractor = FeatureExtractor()
        val events = mutableListOf<KeystrokeEvent>()
        var t = 1000.0

        // 1st key: normal dwell 80ms
        events.add(KeystrokeEvent(1, 0, t))
        t += 80.0
        events.add(KeystrokeEvent(1, 1, t))

        // Normal flight 120ms
        t += 120.0

        // 2nd key: normal dwell 90ms
        events.add(KeystrokeEvent(2, 0, t))
        t += 90.0
        events.add(KeystrokeEvent(2, 1, t))

        // Inter-word pause of 1500ms
        t += 1500.0

        // 3rd key: normal dwell 85ms
        events.add(KeystrokeEvent(3, 0, t))
        t += 85.0
        events.add(KeystrokeEvent(3, 1, t))

        val fv = extractor.extract(events)
        assertNotNull(fv)
        assertTrue(fv!!.isValid())

        // The 1500ms flight must be filtered out; only the 120ms flight should remain
        assertEquals(1, fv.flightTimes.size)
        assertEquals(120.0, fv.flightTimes[0], 0.001)
        assertEquals(120.0, fv.flightMean, 0.001)
    }

    @Test
    fun testImpostorRejectionNotZero() {
        // Enrolled person baseline: dwell ~ 80, dwell_std ~ 15, flight ~ 130, flight_std ~ 30
        val enrolledVectors = mutableListOf<DoubleArray>()
        val rnd = java.util.Random(42)
        for (i in 0 until 15) {
            val dMean = 80.0 + rnd.nextGaussian() * 4.0
            val dStd = 15.0 + rnd.nextGaussian() * 2.0
            val fMean = 130.0 + rnd.nextGaussian() * 8.0
            val fStd = 30.0 + rnd.nextGaussian() * 3.0
            val iMean = dMean + fMean
            val iStd = Math.sqrt(dStd * dStd + fStd * fStd)
            enrolledVectors.add(doubleArrayOf(dMean, dStd, fMean, fStd, iMean, iStd))
        }

        val ocsvm = OCSVMModel()
        ocsvm.train(enrolledVectors)

        val mahal = MahalanobisNormalizer()
        mahal.fit(enrolledVectors)

        val scorer = RiskScorer()

        // 1. Legitimate user window
        val legit = doubleArrayOf(81.0, 15.5, 128.0, 31.0, 209.0, 34.5)
        val legitOcsvm = ocsvm.anomalyScore(legit)
        val legitMahal = mahal.normalizedScore(legit)
        val legitDec = scorer.score(legitOcsvm, legitMahal)

        // Legitimate must be low risk (< 0.35)
        assertTrue("Legit score should be < 0.35, was: ${legitDec.compositeScore}", legitDec.compositeScore < 0.35)
        assertEquals(Action.ALLOW, legitDec.action)

        // 2. Impostor user window (significantly different rhythm: dwell 125, flight 240)
        val impostor = doubleArrayOf(125.0, 35.0, 240.0, 65.0, 365.0, 73.0)
        val impOcsvm = ocsvm.anomalyScore(impostor)
        val impMahal = mahal.normalizedScore(impostor)

        // Reset history so scorer evaluates without prior legit bias
        scorer.resetHistory()
        val impDec = scorer.score(impOcsvm, impMahal)

        // The impostor composite score MUST NOT be 0.0xx; it should be high (> 0.50)
        assertTrue("Impostor OCSVM score must be high, was: $impOcsvm", impOcsvm > 0.50)
        assertTrue("Impostor Mahal score must be high, was: $impMahal", impMahal > 0.50)
        assertTrue("Impostor composite must be > 0.50, was: ${impDec.compositeScore}", impDec.compositeScore > 0.50)
    }
}