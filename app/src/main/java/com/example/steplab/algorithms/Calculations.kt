package com.example.steplab.algorithms

import android.hardware.SensorManager
import java.math.BigDecimal
import java.math.MathContext

class Calculations(
    private val configuration: Configuration = Configuration()
) {

    /**
     * Computes the resultant magnitude of a 3-component vector using the original Java formula.
     */
    fun resultant(vector: Array<BigDecimal>): BigDecimal {
        val x = vector[0].toDouble()
        val y = vector[1].toDouble()
        val z = vector[2].toDouble()
        
        // Use the exact formula from Java: sqrt((sqrt(x^2 + y^2))^2 + z^2)
        val xyMagnitude = Math.sqrt(Math.pow(x, 2.0) + Math.pow(y, 2.0))
        val result = Math.sqrt(Math.pow(xyMagnitude, 2.0) + Math.pow(z, 2.0))
        
        return BigDecimal.valueOf(result)
    }

    /**
     * Computes linear acceleration by subtracting gravity.
     */
    fun linearAcceleration(resultant: BigDecimal): BigDecimal {
        return resultant.subtract(configuration.gravity)
    }

    private val rotationMatrix = FloatArray(16)
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    
    // Reuse arrays to reduce object creation in rotation matrix calculations
    private val worldResult = Array(3) { BigDecimal.ZERO }
    
    // Cache for rotation matrix to avoid expensive recalculations
    private val lastAccel = FloatArray(3)
    private val lastMagnet = FloatArray(3)
    private var rotationMatrixCached = false

    /**
     * Calculates and stores the 3x3 rotation matrix based on accelerometer and magnetometer readings.
     * Uses the exact same indices as the Java version.
     */
    fun updateRotationMatrix(
        accel: Array<BigDecimal>,
        magnet: Array<BigDecimal>
    ) {
        // copy sensor values to float arrays
        for (i in accel.indices) {
            gravity[i] = accel[i].toFloat()
            geomagnetic[i] = magnet[i].toFloat()
        }

        val success = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
        if (success) {
            val matrix3x3 = arrayOf(
                arrayOf(
                    BigDecimal.valueOf(rotationMatrix[0].toDouble()),
                    BigDecimal.valueOf(rotationMatrix[1].toDouble()),
                    BigDecimal.valueOf(rotationMatrix[2].toDouble())
                ),
                arrayOf(
                    BigDecimal.valueOf(rotationMatrix[4].toDouble()),
                    BigDecimal.valueOf(rotationMatrix[5].toDouble()),
                    BigDecimal.valueOf(rotationMatrix[6].toDouble())
                ),
                arrayOf(
                    BigDecimal.valueOf(rotationMatrix[8].toDouble()),
                    BigDecimal.valueOf(rotationMatrix[9].toDouble()),
                    BigDecimal.valueOf(rotationMatrix[10].toDouble())
                )
            )
            configuration.rotationMatrix = matrix3x3
            rotationMatrixCached = true
        }
    }

    /**
     * Transforms accelerometer readings to fixed (world) coordinate system.
     */
    fun worldAcceleration(accel: Array<BigDecimal>): Array<BigDecimal> {
        for (i in 0 until 3) {
            var sum = BigDecimal.ZERO
            for (j in 0 until 3) {
                sum = sum.add(
                    configuration.rotationMatrix[i][j]
                        .multiply(accel[j], MathContext.DECIMAL32),
                    MathContext.DECIMAL32
                )
            }
            worldResult[i] = sum
        }
        return worldResult
    }

    /**
     * Checks if the current step is a false step.
     */
    fun checkFalseStep(
        last4Steps: MutableList<Float>,
        currentStepMean: Float
    ): Boolean {
        var mK = 0f
        for (value in last4Steps) {
            mK += value
        }

        val extendedSteps = ArrayList(last4Steps)
        extendedSteps.add(currentStepMean)

        var mK2 = 0f
        for (value in extendedSteps) {
            mK2 += value
        }

        val mCalc = mK / 4
        val m2Calc = mK2 / 5

        val threshMagn = kotlin.math.sqrt((mCalc - m2Calc) * (mCalc - m2Calc))

        return threshMagn > 1.2
    }

    /**
     * Calculates the sum of the values in a list.
     */
    fun sumOfMagnet(magnetValues: List<Float>): Float {
        var sum = 0f
        for (value in magnetValues) {
            sum += value
        }
        return sum
    }

}
