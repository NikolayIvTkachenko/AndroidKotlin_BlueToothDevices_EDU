package com.rsh.tkachenkoni.app_version_07_ble_04_example.models


import android.bluetooth.BluetoothGattCharacteristic.*
import com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model.BluetoothBytesParser
import java.io.Serializable


/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
class PulseOximeterContinuousMeasurement(value: ByteArray?) : Serializable {
    private val SpO2: Int
    private val pulseRate: Int
    private var SpO2Fast = 0
    private var pulseRateFast = 0
    private var SpO2Slow = 0
    private var pulseRateSlow = 0
    private var pulseAmplitudeIndex = 0f
    private var measurementStatus = 0
    private var sensorStatus = 0
    fun getSpO2(): Int {
        return SpO2
    }

    fun getPulseRate(): Int {
        return pulseRate
    }

    fun getSpO2Fast(): Int {
        return SpO2Fast
    }

    fun getPulseRateFast(): Int {
        return pulseRateFast
    }

    fun getSpO2Slow(): Int {
        return SpO2Slow
    }

    fun getPulseRateSlow(): Int {
        return pulseRateSlow
    }

    fun getPulseAmplitudeIndex(): Float {
        return pulseAmplitudeIndex
    }

    fun getMeasurementStatus(): Int {
        return measurementStatus
    }

    fun getSensorStatus(): Int {
        return sensorStatus
    }

    override fun toString(): String {
        return if (SpO2 == 2047 || pulseRate == 2047) {
            "invalid measurement"
        } else String.format(
            "SpO2 %d%%, Pulse %d bpm, PAI %.1f",
            SpO2,
            pulseRate,
            pulseAmplitudeIndex
        )
    }

    init {
        val parser = value?.let { BluetoothBytesParser(it) }
        val flags: Int = parser!!.getIntValue(FORMAT_UINT8)
        val spo2FastPresent = flags and 0x01 > 0
        val spo2SlowPresent = flags and 0x02 > 0
        val measurementStatusPresent = flags and 0x04 > 0
        val sensorStatusPresent = flags and 0x08 > 0
        val pulseAmplitudeIndexPresent = flags and 0x10 > 0
        SpO2 = parser.getFloatValue(FORMAT_SFLOAT).toInt()
        pulseRate = parser.getFloatValue(FORMAT_SFLOAT).toInt()
        if (spo2FastPresent) {
            SpO2Fast = parser.getFloatValue(FORMAT_SFLOAT).toInt()
            pulseRateFast = parser.getFloatValue(FORMAT_SFLOAT).toInt()
        }
        if (spo2SlowPresent) {
            SpO2Slow = parser.getFloatValue(FORMAT_SFLOAT).toInt()
            pulseRateSlow = parser.getFloatValue(FORMAT_SFLOAT).toInt()
        }
        if (measurementStatusPresent) {
            measurementStatus = parser.getIntValue(FORMAT_UINT16)
        }
        if (sensorStatusPresent) {
            sensorStatus = parser.getIntValue(FORMAT_UINT16)
            val reservedByte: Int = parser.getIntValue(FORMAT_UINT8)
        }
        if (pulseAmplitudeIndexPresent) {
            pulseAmplitudeIndex = parser.getFloatValue(FORMAT_SFLOAT)
        }
    }
}