package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model

import android.bluetooth.BluetoothDevice.*


/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
enum class PeripheralType(value: Int) {
    /**
     * Unknown peripheral type, peripheral is not cached
     */
    UNKNOWN(DEVICE_TYPE_UNKNOWN),

    /**
     * Classic - BR/EDR peripheral
     */
    CLASSIC(DEVICE_TYPE_CLASSIC),

    /**
     * Bluetooth Low Energy peripheral
     */
    LE(DEVICE_TYPE_LE),

    /**
     * Dual Mode - BR/EDR/LE
     */
    DUAL(DEVICE_TYPE_DUAL);

    val value: Int

    companion object {
        fun fromValue(value: Int): PeripheralType {
            for (type in values()) {
                if (type.value == value) {
                    return type
                }
            }
            return UNKNOWN
        }
    }

    init {
        this.value = value
    }
}