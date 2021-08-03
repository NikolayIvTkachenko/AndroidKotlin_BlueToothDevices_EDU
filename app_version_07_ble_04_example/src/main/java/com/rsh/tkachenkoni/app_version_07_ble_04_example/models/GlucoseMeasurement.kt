package com.rsh.tkachenkoni.app_version_07_ble_04_example.models

import android.bluetooth.BluetoothGattCharacteristic.*
import com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model.BluetoothBytesParser
import java.io.Serializable
import java.util.*


/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
class GlucoseMeasurement(byteArray: ByteArray?) : Serializable {
    val unit: GlucoseMeasurementUnit
    var timestamp: Date
    var sequenceNumber: Int
    var contextWillFollow: Boolean
    var value = 0f
    override fun toString(): String {
        return String.format(
            Locale.ENGLISH,
            "%.1f %s, at (%s)",
            value,
            if (unit === GlucoseMeasurementUnit.MmolPerLiter) "mmol/L" else "mg/dL",
            timestamp
        )
    }

    init {
        val parser = byteArray?.let { BluetoothBytesParser(it) }

        // Parse flags
        val flags: Int = parser!!.getIntValue(FORMAT_UINT8)
        val timeOffsetPresent = flags and 0x01 > 0
        val typeAndLocationPresent = flags and 0x02 > 0
        unit = if (flags and 0x04 > 0) GlucoseMeasurementUnit.MmolPerLiter else GlucoseMeasurementUnit.MiligramPerDeciliter
        contextWillFollow = flags and 0x10 > 0

        // Sequence number is used to match the reading with an optional glucose context
        sequenceNumber = parser.getIntValue(FORMAT_UINT16)

        // Read timestamp
        timestamp = parser.getDateTime()
        if (timeOffsetPresent) {
            val timeOffset: Int = parser.getIntValue(FORMAT_SINT16)
            timestamp = Date(timestamp.time + timeOffset * 60000)
        }
        if (typeAndLocationPresent) {
            val glucoseConcentration: Float = parser.getFloatValue(FORMAT_SFLOAT)
            val multiplier = if (unit === GlucoseMeasurementUnit.MiligramPerDeciliter) 100000 else 1000
            value = glucoseConcentration * multiplier
        }
    }
}