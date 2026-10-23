package com.datalaw.pdfscanner.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/** How steady the phone is: a smoothed angular speed (rad/s) from the gyroscope. No gyroscope = always steady. */
class Gyro(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    @Volatile var rate = 0f
        private set

    val steady: Boolean get() = rate < 0.15f

    fun start() { sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } }
    fun stop() { sm.unregisterListener(this) }

    override fun onSensorChanged(e: SensorEvent) {
        val v = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        rate = 0.8f * rate + 0.2f * v
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
}
