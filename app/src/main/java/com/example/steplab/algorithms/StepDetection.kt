package com.example.steplab.algorithms

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.floor
import kotlin.math.ceil

/**
 * Implements real-time step detection using various strategies.
 */
class StepDetection(
    private val configuration: Configuration
) {
    private var previousDifference: BigDecimal = BigDecimal.ZERO
    private var previousMatch: Int = -1
    private var thresholdSum: BigDecimal = configuration.detectionThreshold
    private var eventCount: Int = 1

    /**
     * Step counting via autocorrelation (paper pipeline) + robust fixes.
     * - decimazione (se fs alto) verso ~50–60 Hz
     * - filtro band-pass
     * - ACF normalizzata (biased)
     * - k stimato su finestre ma usato per l’intero segmento (no doppio conteggio)
     * - clamp dei passi del segmento e del totale alla cadenza attesa
     */
    fun countStepsAutocorrelation(
        samples: List<Array<BigDecimal>>,
        fs: Int,
        filters: Filters,
        calculations: Calculations,
        useHannWindowForFFT: Boolean = true,
        dropHeadSecondsForMSD: Double = 0.3,
        bpOrder: Int = 6,
        decimateHighFs: Boolean = true
    ): Calculations.AutoCorrStepResult {
        println("=== StepDetection.countStepsAutocorrelation START ===")
        println("Input: ${samples.size} samples, fs=$fs Hz")

        require(samples.isNotEmpty()) { "samples is empty" }
        require(fs > 1) { "fs must be > 1 Hz" }

        // 1) Magnitude & remove DC
        val magNoDcFull = calculations.computeMagnitudeWithoutDC(samples)

        // 1b) Decimazione sicura verso ~50–60 Hz se fs è alto
        val (magNoDc, fsUse) =
            if (decimateHighFs && fs > 120) calculations.downsampleForWalking(magNoDcFull, fs, 50)
            else magNoDcFull to fs

        println("   Using fs=$fsUse Hz (decimated=${fsUse != fs})")

        // 2) f0 globale
        val f0 = calculations.estimateFundamentalFrequency(magNoDc, fsUse, useHannWindowForFFT)
        println("   Estimated global f0: $f0 Hz")

        // 3) Banda passante & filtro
        val (low, high) = calculations.computeBandPassRange(f0, fsUse)
        println("   Band-pass range: $low - $high Hz (order=$bpOrder)")
        val y = filters.filterMagnitudeBandPassSeries(
            magNoDc = magNoDc,
            fs = fsUse,
            low = low,
            high = high,
            order = bpOrder
        )

        // 4) Rilevatore di attività via MSD
        val msd = calculations.computeMovingStandardDeviation(y)
        val skip = (dropHeadSecondsForMSD * fsUse).toInt().coerceAtLeast(0)
        val msdThr = calculations.computeStandardDeviation(msd)
        val segs = calculations.findSegmentsAboveThreshold(msd, msdThr, skip)
        println("   MSD threshold=$msdThr, segments=${segs.size}")

        // 5) ACF per segmento: finestre solo per stimare k, no somma passi per finestra
        val lags = mutableListOf<Int>()
        val peaks = mutableListOf<Double>()
        var totalStepsRaw = 0
        var activeSamples = 0

        for ((idx, segPair) in segs.withIndex()) {
            val (s, e) = segPair
            val seg = y.copyOfRange(s, e + 1)
            val segLen = seg.size
            activeSamples += segLen

            val msdMean = if (e >= s) msd.copyOfRange(s, e + 1).average() else 0.0
            println("   Segment $idx: [$s,$e], len=$segLen, msdMean=$msdMean")

            if (segLen < 8) {
                lags += 0; peaks += 0.0
                continue
            }

            // Finestre per stimare k (2.5 s con hop 1.25 s)
            val win = (2.5 * fsUse).roundToInt()
            val hop = (1.25 * fsUse).roundToInt()

            val kCandidates = mutableListOf<Double>()
            val vCandidates = mutableListOf<Double>()

            fun processWindow(sub: DoubleArray) {
                // f0 locale e k0 atteso
                val f0w = calculations.estimateFundamentalFrequency(sub, fsUse, useHannWindowForFFT)
                val k0 = (fsUse / max(0.5, f0w))
                val kMin = max(1, (k0 * 0.70).roundToInt())
                val kMax = min(sub.size - 2, (k0 * 1.30).roundToInt())

                val rho = calculations.computeNormalizedAutocorrelation(sub)

                // Picco nel range [kMin, kMax] con soglia 0.70 → retry 0.60
                fun findPeakInRange(thr: Double): Pair<Int, Double> {
                    var bestK = 0; var bestV = 0.0
                    for (i in max(1, kMin)..min(kMax, rho.lastIndex - 1)) {
                        if (rho[i] > thr && rho[i] >= rho[i-1] && rho[i] >= rho[i+1] && rho[i] > bestV) {
                            bestK = i; bestV = rho[i]
                        }
                    }
                    return bestK to bestV
                }

                var (kInt, v) = findPeakInRange(0.70)
                if (kInt == 0) { val r = findPeakInRange(0.60); kInt = r.first; v = r.second }

                var kStar = if (kInt > 0) refineParabolic(rho, kInt) else 0.0
                if (kStar <= 0.0 && msdMean > msdThr) kStar = k0 // ultimo fallback

                if (kStar > 0.0) {
                    kCandidates += kStar
                    vCandidates += v
                }
            }

            if (segLen >= win + 4) {
                var t = 0
                while (t < segLen) {
                    val end = min(segLen, t + win)
                    if (end - t >= max(8, (0.6 * fsUse).toInt())) {
                        val sub = seg.copyOfRange(t, end)
                        processWindow(sub)
                    }
                    t += hop
                }
            } else {
                processWindow(seg)
            }

            // 5b) Passi del segmento: UNA sola stima da k mediano (niente somma per finestra)
            val stepsSeg = if (kCandidates.isNotEmpty()) {
                val kMed = median(kCandidates)
                val vMax = vCandidates.maxOrNull() ?: 0.0
                val stepsEst = (segLen / kMed).roundToInt().coerceAtLeast(1)

                // clamp alla cadenza globale attesa per la durata del segmento (tol adattiva)
                val expectedSeg = (segLen.toDouble() / fsUse) * f0
                val tolSeg = tolFromPeak(vMax)
                val stepsClamped = clampToCadence(stepsEst, expectedSeg, tol = tolSeg)

                lags += kMed.roundToInt()
                peaks += vMax
                println(
                    "     -> stepsSeg=$stepsClamped (est=$stepsEst, exp=${"%.1f".format(expectedSeg)}, tol=${"%.2f".format(tolSeg)}), " +
                            "k~${"%.1f".format(kMed)}, vMax=${"%.3f".format(vMax)}"
                )
                stepsClamped
            } else {
                // fallback: nessun picco trovato -> usa k da f0 globale
                val kGuess = (fsUse / max(0.5, f0)).roundToInt().coerceAtLeast(1)
                val stepsEst = (segLen.toDouble() / kGuess).roundToInt().coerceAtLeast(1)

                val expectedSeg = (segLen.toDouble() / fsUse) * f0
                val tolSeg = tolFromPeak(0.0) // più permissiva
                val stepsClamped = clampToCadence(stepsEst, expectedSeg, tol = tolSeg)

                lags += kGuess
                peaks += 0.0
                println("     -> fallback (no peak): kGuess=$kGuess, steps=$stepsClamped (est=$stepsEst, exp=${"%.1f".format(expectedSeg)}, tol=${"%.2f".format(tolSeg)})")
                stepsClamped
            }

            totalStepsRaw += stepsSeg
        }

        // 6) Clamp globale sul totale in base alla cadenza attesa nella porzione attiva (tol adattiva globale)
        val expectedTotal = (activeSamples.toDouble() / fsUse) * f0
        val peakStrengthGlobal = peaks.filter { it > 0.0 }.let { if (it.isEmpty()) 0.0 else it.average() }
        val tolGlobal = tolFromPeak(peakStrengthGlobal)
        val finalSteps = clampToCadence(totalStepsRaw, expectedTotal, tol = tolGlobal)

        println("Total steps (raw=$totalStepsRaw, clamped=$finalSteps, activeSamples=$activeSamples, fs=$fsUse, f0=$f0, tolGlobal=${"%.2f".format(tolGlobal)})")

        val result = Calculations.AutoCorrStepResult(
            steps = finalSteps,
            f0Hz = f0,
            bandLowHz = low,
            bandHighHz = high,
            segments = segs,
            lagsPerSegment = lags,
            autocorrPeaks = peaks
        )

        println("=== StepDetection.countStepsAutocorrelation END ===")
        return result
    }

    // ===== Helper privati =====

    /** Mappa l'ampiezza del picco ACF a una tolleranza relativa (più picco forte → più stretto). */
    private fun tolFromPeak(v: Double): Double = when {
        v >= 0.78 -> 0.20
        v >= 0.72 -> 0.25
        v >= 0.65 -> 0.30
        else      -> 0.35
    }

    /** Raffinamento parabolico del picco ACF con 3 punti (k-1, k, k+1). */
    private fun refineParabolic(rho: DoubleArray, k: Int): Double {
        if (k <= 0 || k >= rho.size - 1) return k.toDouble()
        val y1 = rho[k - 1]
        val y2 = rho[k]
        val y3 = rho[k + 1]
        val denom = (y1 - 2.0 * y2 + y3)
        if (denom == 0.0) return k.toDouble()
        val delta = 0.5 * (y1 - y3) / denom
        return k + delta
    }

    /** Mediana robusta di una lista di Double. */
    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2]
        else 0.5 * (sorted[n / 2 - 1] + sorted[n / 2])
    }

    /**
     * Clamp dei passi (intero) a una forchetta centrata sull'atteso (double) con tolleranza relativa.
     * Esempio: tol=0.30 -> [0.7*expected, 1.3*expected].
     */
    private fun clampToCadence(stepsEst: Int, expected: Double, tol: Double): Int {
        val minAllowed = max(1, floor(expected * (1.0 - tol)).toInt())
        val maxAllowed = max(minAllowed, ceil(expected * (1.0 + tol)).toInt())
        return stepsEst.coerceIn(minAllowed, maxAllowed)
    }

    /**
     * Peak-and-difference detection: adaptive threshold based on local extrema difference.
     * Returns true if a step is detected.
     */
    fun detectStepByPeakDifference(): Boolean {
        val localMax = configuration.lastLocalMaxAccel
        if (localMax > BigDecimal("10.5")) {
            val dynamicThreshold = configuration.detectionThreshold
                .multiply(BigDecimal("3"), MathContext.DECIMAL32)
                .divide(BigDecimal("5"), MathContext.DECIMAL32)

            val extremaDiff = configuration.localExtremaDifference
            if (extremaDiff > dynamicThreshold) {
                thresholdSum = thresholdSum.add(extremaDiff)
                eventCount++
                configuration.detectionThreshold = thresholdSum
                    .divide(BigDecimal.valueOf(eventCount.toLong()), MathContext.DECIMAL32)
                configuration.lastDetectedStepTime = configuration.lastStepSecondPhaseTime
                return true
            } else {
                thresholdSum = thresholdSum.add(configuration.detectionThreshold)
                eventCount++
                configuration.detectionThreshold = thresholdSum
                    .divide(BigDecimal.valueOf(eventCount.toLong()), MathContext.DECIMAL32)
            }
        }
        return false
    }

    /**
     * Crossing-time detection: detects if step interval spans the last x-axis intersection.
     */
    fun detectStepByCrossing(): Boolean {
        val firstTime = configuration.lastStepFirstPhaseTime
        val secondTime = configuration.lastStepSecondPhaseTime
        val intersectionTime = configuration.lastXAxisIntersectionTime

        return if (firstTime < secondTime) {
            firstTime < intersectionTime && secondTime > intersectionTime
        } else {
            // fix for Bagilevi filter mode
            secondTime < intersectionTime && firstTime > intersectionTime
        }
    }

    private var stepDetected: Boolean = false
    private var currentDifference: BigDecimal = BigDecimal.ZERO

    /**
     * Bagilevi-based detection: matches peaks roughly consistent with previous differences.
     * @param match indicator of current peak (e.g., 0 or 1)
     */
    fun detectStepByBagilevi(match: Int): Boolean {
        stepDetected = false
        if (configuration.continuousBagileviDetection) {
            currentDifference = configuration.localExtremaDifference

            if (currentDifference > BigDecimal("10")) {
                val condition1 = currentDifference > previousDifference
                    .multiply(BigDecimal("2").divide(BigDecimal("3"), MathContext.DECIMAL32), MathContext.DECIMAL32)
                val condition2 = previousDifference > currentDifference
                    .divide(BigDecimal("3"), MathContext.DECIMAL32)
                val condition3 = previousMatch != (1 - match)

                if (condition1 && condition2 && condition3) {
                    previousMatch = match
                    stepDetected = true
                } else {
                    previousMatch = -1
                }
            }
            previousDifference = currentDifference
        }
        return stepDetected
    }
}
