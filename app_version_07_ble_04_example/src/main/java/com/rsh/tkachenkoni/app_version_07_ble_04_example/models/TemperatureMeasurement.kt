package com.rsh.tkachenkoni.app_version_07_ble_04_example.models

import android.bluetooth.BluetoothGattCharacteristic.FORMAT_FLOAT
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
class TemperatureMeasurement(byteArray: ByteArray?) : Serializable {
    var unit: TemperatureUnit
    var temperatureValue: Float
    var timestamp: Date? = null
    var type: TemperatureType? = null
    override fun toString(): String {
        val df: DateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH)
        val formattedTimestamp: String
        formattedTimestamp = if (timestamp != null) {
            df.format(timestamp)
        } else {
            "null"
        }
        return java.lang.String.format(
            Locale.ENGLISH,
            "%.1f %s (%s), at (%s)",
            temperatureValue,
            if (unit === TemperatureUnit.Celsius) "celcius" else "fahrenheit",
            type,
            formattedTimestamp
        )
    }

    init {
        val parser = byteArray?.let { BluetoothBytesParser(it) }

        // Parse flag byte
        val flags: Int = parser!!.getIntValue(FORMAT_UINT8)
        unit = if (flags and 0x01 > 0) TemperatureUnit.Fahrenheit else TemperatureUnit.Celsius
        val timestampPresent = flags and 0x02 > 0
        val typePresent = flags and 0x04 > 0

        // Get temperature value
        temperatureValue = parser!!.getFloatValue(FORMAT_FLOAT)

        // Get timestamp
        if (timestampPresent) {
            timestamp = parser.getDateTime()
        }

        // Get temperature type
        if (typePresent) {
            val typeValue: Int = parser.getIntValue(FORMAT_UINT8)
            type = TemperatureType.fromValue(typeValue)
        }
    }
}