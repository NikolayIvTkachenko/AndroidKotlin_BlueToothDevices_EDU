package com.rsh.tkachenkoni.app_version_07_ble_04_example.models

import android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT16
import android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8
import com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model.BluetoothBytesParser
import java.io.Serializable
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
class WeightMeasurement(byteArray: ByteArray?) : Serializable {
    val weight: Double
    val unit: WeightUnit
    var timestamp: Date? = null
    var userID: Int? = null
    var BMI: Int? = null
    var height: Int? = null
    override fun toString(): String {
        val df: DateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss")
        val formattedTimestamp = if (timestamp != null) df.format(timestamp) else "null"
        return String.format(
            "%.1f %s, user %d, BMI %d, height %d at (%s)",
            weight,
            if (unit === WeightUnit.Kilograms) "kg" else "lb",
            userID,
            BMI,
            height,
            formattedTimestamp
        )
    }

    init {
        val parser = byteArray?.let { BluetoothBytesParser(it) }

        // Parse flag byte
        val flags: Int = parser!!.getIntValue(BluetoothBytesParser.FORMAT_UINT8)
        unit = if (flags and 0x01 > 0) WeightUnit.Pounds else WeightUnit.Kilograms
        val timestampPresent = flags and 0x02 > 0
        val userIDPresent = flags and 0x04 > 0
        val bmiAndHeightPresent = flags and 0x08 > 0

        // Get weight value
        val weightMultiplier = if (unit === WeightUnit.Kilograms) 0.005 else 0.01
        weight = parser.getIntValue(FORMAT_UINT16) * weightMultiplier

        // Get timestamp if present
        timestamp = if (timestampPresent) {
            parser.getDateTime()
        } else {
            Calendar.getInstance().time
        }

        // Get user ID if present
        if (userIDPresent) {
            userID = parser.getIntValue(FORMAT_UINT8)
        }

        // Get BMI and Height if present
        if (bmiAndHeightPresent) {
            BMI = parser.getIntValue(FORMAT_UINT16)
            height = parser.getIntValue(FORMAT_UINT16)
        }
    }
}