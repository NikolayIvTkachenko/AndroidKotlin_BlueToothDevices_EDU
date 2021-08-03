package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model

import android.bluetooth.BluetoothDevice

/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
enum class Transport(value: Int) {
    /**
     * No preference of physical transport for GATT connections to remote dual-mode devices
     */
    AUTO(BluetoothDevice.TRANSPORT_AUTO),

    /**
     * Prefer BR/EDR transport for GATT connections to remote dual-mode devices is necessary.
     */
    BR_EDR(BluetoothDevice.TRANSPORT_BREDR),

    /**
     * Prefer LE transport for GATT connections to remote dual-mode devices
     */
    LE(BluetoothDevice.TRANSPORT_LE);

    val value: Int

    companion object {
        fun fromValue(value: Int): Transport {
            for (transport in values()) {
                if (transport.value == value) return transport
            }
            return AUTO
        }
    }

    init {
        this.value = value
    }
}