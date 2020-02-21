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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
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

    private var startedTime: Long = 0

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(
            this, R.layout.activity_main
        )
        binding.stopwatchSeconds = 0.toLong()
        binding.stopwatchMs = 0.toLong()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        listenedSensors = listOf(
            sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (!binding.started) return

        val sensorType = getSensorType(event?.sensor?.type)
        val time = Calendar.getInstance().time.time - initialTime

        if (time != lastTimeValues[sensorType]) {
            lastTimeValues[sensorType] = time
            val newRow = event?.values?.joinToString(separator = ",", postfix = "\n") ?: ""
            sensorValues[sensorType] += "$time,$newRow"
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

    private fun getSensorType(number: Int?): String {
        return when (number) {
            Sensor.TYPE_ACCELEROMETER -> "accelerometer"
            Sensor.TYPE_GYROSCOPE -> "gyroscope"
            Sensor.TYPE_PRESSURE -> "pressure"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "temperature"
            else -> ""
        }
    }

    fun startOrStop(view: View) {
        binding.started = !binding.started
        if (binding.started) {
            launch {
                startedTime = System.currentTimeMillis()
                while (binding.started) {
                    val currentTime = System.currentTimeMillis() - startedTime
                    binding.stopwatchSeconds = currentTime / 1000
                    binding.stopwatchMs = (currentTime % 1000) / 100
                    delay(100)
                }
            }
        } else {
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
                sensorValues[getSensorType(sensor.type)] = ""
                initialTime = Calendar.getInstance().time.time
                lastTimeValues[getSensorType(sensor.type)] = 0.toLong()
            }
        }
    }
}
