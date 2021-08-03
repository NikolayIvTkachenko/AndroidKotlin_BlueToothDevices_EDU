package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model

import android.bluetooth.BluetoothGattCharacteristic.*


/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
enum class WriteType(writeType: Int, property: Int) {
    /**
     * Write characteristic and requesting acknowledgement by the remote peripheral
     */
    WITH_RESPONSE(WRITE_TYPE_DEFAULT, PROPERTY_WRITE),

    /**
     * Write characteristic without requiring a response by the remote peripheral
     */
    WITHOUT_RESPONSE(WRITE_TYPE_NO_RESPONSE, PROPERTY_WRITE_NO_RESPONSE),

    /**
     * Write characteristic including authentication signature
     */
    SIGNED(WRITE_TYPE_SIGNED, PROPERTY_SIGNED_WRITE);

    val writeType: Int
    val property: Int

    init {
        this.writeType = writeType
        this.property = property
    }
}