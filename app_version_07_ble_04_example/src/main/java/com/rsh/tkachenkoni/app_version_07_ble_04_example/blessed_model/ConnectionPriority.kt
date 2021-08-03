package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model

import android.bluetooth.BluetoothGatt.*


/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
enum class ConnectionPriority(value: Int) {
    /**
     * Use the connection parameters recommended by the Bluetooth SIG.
     * This is the default value if no connection parameter update
     * is requested.
     */
    BALANCED(CONNECTION_PRIORITY_BALANCED),

    /**
     * Request a high priority, low latency connection.
     * An application should only request high priority connection parameters to transfer large
     * amounts of data over LE quickly. Once the transfer is complete, the application should
     * request BALANCED connection parameters to reduce energy use.
     */
    HIGH(CONNECTION_PRIORITY_HIGH),

    /**
     * Request low power, reduced data rate connection parameters.
     */
    LOW_POWER(CONNECTION_PRIORITY_LOW_POWER);

    val value: Int

    init {
        this.value = value
    }
}