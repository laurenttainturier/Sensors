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

    private var sensorValues: MutableMap<String, MutableMap<Long, List<Float>>> = mutableMapOf()

    private val lastTimeValues: MutableMap<String, Long> = mutableMapOf()

    private var initialTime: Long = 0

    private val sensors: Map<Int, String> = mapOf(
        Sensor.TYPE_ACCELEROMETER to "accelerometer",
        Sensor.TYPE_GRAVITY to "gravity"/*
        Sensor.TYPE_GYROSCOPE to "gyroscope",
        Sensor.TYPE_PRESSURE to "pressure",
        Sensor.TYPE_AMBIENT_TEMPERATURE to "temperature",
        Sensor.TYPE_MAGNETIC_FIELD to "magnetic"*/
    )

    private var gravity = listOf<Float>()

    private val separator = ";"

    private val digitComma = ","

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

        binding.time = "0"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (!binding.started || event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> gravity = event.values.toList()
            Sensor.TYPE_ACCELEROMETER -> {
                val sensorType = getSensorName(event.sensor.type)
                val time = Calendar.getInstance().time.time - initialTime
                if (gravity.isNotEmpty())
                    sensorValues[sensorType]?.set(time, event.values.mapIndexed { i, value ->
                        value - gravity[i]
                    })
                binding.time = "%.2f".format(time.toFloat() / 1000)
            }
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
            val directory = ContextCompat
                .getExternalFilesDirs(applicationContext, null)[0]
            val currentTime: String =
                SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.getDefault()).format(Date())

            sensorValues.forEach { (sensor, data) ->
                val file = File(directory, "$currentTime - ${sensor}.csv")
                FileOutputStream(file).use {
                    // Header of a csv file
                    it.write(
                        "time,ax,ay,az,vx,vy,vz,x,y,z\n"
                            .replace(",", separator)
                            .toByteArray()
                    )

                    var previousTime = 0L
                    val velocity: MutableMap<Long, List<Float>> = mutableMapOf()
                    val displacement: MutableMap<Long, List<Float>> = mutableMapOf()

                    data.forEach { (timestamp, values) ->
                        val dt = timestamp - previousTime

                        // Computes the velocity
                        val newVelocity: List<Float> = values.mapIndexed { i, value ->
                            (velocity[previousTime]?.get(i) ?: 0F) + value * dt / 1000
                        }
                        velocity[timestamp] = newVelocity

                        // Computes the displacement
                        val newDisplacement: List<Float> = newVelocity.mapIndexed { i, value ->
                            (displacement[previousTime]?.get(i) ?: 0F) + value * dt / 1000
                        }
                        displacement[timestamp] = newDisplacement
                        previousTime = timestamp

                        // Transforms acceleration, velocity end displacement into a string
                        val newRow = (values.toList() + newVelocity + newDisplacement)
                            .map { it.toBigDecimal().setScale(4, RoundingMode.HALF_EVEN) }
                            .joinToString(separator = separator, postfix = "\n")
                            .replace(".", digitComma)
                        it.write("$timestamp;$newRow".toByteArray())
                    }
                }
            }
        }

        initRecord()
    }

    private fun initRecord() {
        listenedSensors.forEach { sensor ->
            if (sensor != null) {
                val sensorType = getSensorName(sensor.type)
                sensorValues[sensorType] = mutableMapOf()
                initialTime = Calendar.getInstance().time.time
                lastTimeValues[sensorType] = 0.toLong()
            }
        }
    }
}
