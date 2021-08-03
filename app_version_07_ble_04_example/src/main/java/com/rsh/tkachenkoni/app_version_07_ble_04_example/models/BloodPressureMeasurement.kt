package com.rsh.tkachenkoni.app_version_07_ble_04_example.models

import android.bluetooth.BluetoothGattCharacteristic
import com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model.BluetoothBytesParser
import java.io.Serializable
import java.util.*

/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */


class BloodPressureMeasurement(value: ByteArray?) : Serializable {
    var userID: Int? = null
    var systolic: Float
    var diastolic: Float
    var meanArterialPressure: Float
    var timestamp: Date? = null
    var isMMHG: Boolean
    var pulseRate: Float? = null
    override fun toString(): String {
        return String.format(
            Locale.ENGLISH,
            "%.0f/%.0f %s, MAP %.0f, %.0f bpm, user %d at (%s)",
            systolic,
            diastolic,
            if (isMMHG) "mmHg" else "kPa",
            meanArterialPressure,
            pulseRate,
            userID,
            timestamp
        )
    }

    init {
        val parser = value?.let { BluetoothBytesParser(it) }

        // Parse the flags
        val flags: Int = parser!!.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8)
        isMMHG = flags and 0x01 <= 0
        val timestampPresent = flags and 0x02 > 0
        val pulseRatePresent = flags and 0x04 > 0
        val userIdPresent = flags and 0x08 > 0
        val measurementStatusPresent = flags and 0x10 > 0

        // Get systolic, diastolic and mean arterial pressure
        systolic = parser!!.getFloatValue(BluetoothGattCharacteristic.FORMAT_SFLOAT)
        diastolic = parser!!.getFloatValue(BluetoothGattCharacteristic.FORMAT_SFLOAT)
        meanArterialPressure = parser!!.getFloatValue(BluetoothGattCharacteristic.FORMAT_SFLOAT)

        // Read timestamp
        timestamp = if (timestampPresent) {
            parser?.getDateTime()
        } else {
            Calendar.getInstance().time
        }

        // Read pulse rate
        if (pulseRatePresent) {
            pulseRate = parser?.getFloatValue(BluetoothGattCharacteristic.FORMAT_SFLOAT)
        }

        // Read userId
        if (userIdPresent) {
            userID = parser?.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8)
        }
    }
}