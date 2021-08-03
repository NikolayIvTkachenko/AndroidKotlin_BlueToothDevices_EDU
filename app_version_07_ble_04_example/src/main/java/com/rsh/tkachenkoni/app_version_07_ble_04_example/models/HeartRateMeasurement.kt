package com.rsh.tkachenkoni.app_version_07_ble_04_example.models

import android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT16
import android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8
import com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model.BluetoothBytesParser
import java.io.Serializable
import java.util.*


/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
class HeartRateMeasurement(value: ByteArray?) : Serializable {
    val pulse: Int
    override fun toString(): String {
        return String.format(Locale.ENGLISH, "%d", pulse)
    }

    init {
        val parser = value?.let { BluetoothBytesParser(it) }

        // Parse the flags
        val flags: Int = parser!!.getIntValue(FORMAT_UINT8)
        val unit = flags and 0x01
        val sensorContactStatus = flags and 0x06 shr 1
        val energyExpenditurePresent = flags and 0x08 > 0
        val rrIntervalPresent = flags and 0x10 > 0

        // Parse heart rate
        pulse =
            if (unit == 0) parser.getIntValue(FORMAT_UINT8) else parser.getIntValue(FORMAT_UINT16)
    }
}