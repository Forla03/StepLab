package com.example.steplab.algorithms

import android.hardware.SensorManager
import java.math.BigDecimal
import java.math.MathContext

class Calculations(
    private val configuration: Configuration = Configuration()
) {

    /**
     * Computes the resultant magnitude of a 3-component vector.
     */
    fun resultant(vector: Array<BigDecimal>): BigDecimal {
        val x = vector[0].toDouble()
        val y = vector[1].toDouble()
        val z = vector[2].toDouble()
        // sqrt((sqrt(x^2 + y^2))^2 + z^2) simplifies to sqrt(x^2 + y^2 + z^2)
        return BigDecimal.valueOf(Math.sqrt(x * x + y * y + z * z))
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
            // store only the 3x3 part
            val matrix3x3 = Array(3) { row ->
                Array(3) { col ->
                    BigDecimal.valueOf(rotationMatrix[row * 4 + col].toDouble())
                }
            }
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
}
