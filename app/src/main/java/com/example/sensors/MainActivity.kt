package com.example.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.example.sensors.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.CoroutineContext


class MainActivity : AppCompatActivity(), SensorEventListener, CoroutineScope {

    private lateinit var sensorManager: SensorManager

    private lateinit var locationManager: LocationManager

    private lateinit var listenedSensors: List<Sensor?>

    private lateinit var binding: ActivityMainBinding

    private var sensorValues: MutableMap<String, String> = mutableMapOf()

    private val lastTimeValues: MutableMap<String, Long> = mutableMapOf()

    private var initialTime: Long = 0

    private val sensors: Map<Int, String> = mapOf(
        Sensor.TYPE_ACCELEROMETER to "accelerometer",
        Sensor.TYPE_GYROSCOPE to "gyroscope",
        Sensor.TYPE_PRESSURE to "pressure",
        Sensor.TYPE_AMBIENT_TEMPERATURE to "temperature",
        Sensor.TYPE_MAGNETIC_FIELD to "magnetic",
        Sensor.TYPE_GRAVITY to "gravity"
    )

    private fun getSensorName(type: Int): String {
        return sensors[type] ?: ""
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(
            this, R.layout.activity_main
        )

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        listenedSensors = sensors.keys.map { key ->
            sensorManager.getDefaultSensor(key)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (!binding.started || event == null) return

        val sensorType = getSensorName(event.sensor.type)
        val time = Calendar.getInstance().time.time - initialTime

        if (time != lastTimeValues[sensorType]) {
            lastTimeValues[sensorType] = time
            val newRow = event.values
                ?.map { it.toBigDecimal().setScale(4, RoundingMode.HALF_EVEN) }
                ?.joinToString(separator = ";", postfix = "\n") ?: ""
            sensorValues[sensorType] += "$time;$newRow"

            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER)
                binding.accelerometer = newRow
        }
    }

    override fun onResume() {
        super.onResume()
        listenedSensors.forEach { sensor ->
            sensorManager.registerListener(
                this, sensor, SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    fun startOrStop(view: View) {
        binding.started = !binding.started
        if (!binding.started) {
            val directory = ContextCompat.getExternalFilesDirs(applicationContext, null)[0]
            val currentTime: String =
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            sensorValues.forEach { (sensor, data) ->
                val file = File(directory, "$currentTime - ${sensor}.csv")
                FileOutputStream(file).use {
                    it.write(data.toByteArray())
                }
            }
        }

        initRecord()
    }

    private fun initRecord() {
        listenedSensors.forEach { sensor ->
            if (sensor != null) {
                val sensorType = getSensorName(sensor.type)
                sensorValues[sensorType] = "time;x;y;z\n"
                initialTime = Calendar.getInstance().time.time
                lastTimeValues[sensorType] = 0.toLong()
            }
        }
    }
}
