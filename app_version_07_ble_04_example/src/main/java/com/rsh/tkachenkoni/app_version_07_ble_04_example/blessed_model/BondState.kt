package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model

import android.bluetooth.BluetoothDevice.*


/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
enum class BondState(value: Int) {
    /**
     * Indicates the remote peripheral is not bonded.
     * There is no shared link key with the remote peripheral, so communication
     * (if it is allowed at all) will be unauthenticated and unencrypted.
     */
    NONE(BOND_NONE),

    /**
     * Indicates bonding is in progress with the remote peripheral.
     */
    BONDING(BOND_BONDING),

    /**
     * Indicates the remote peripheral is bonded.
     * A shared link keys exists locally for the remote peripheral, so
     * communication can be authenticated and encrypted.
     */
    BONDED(BOND_BONDED);

    val value: Int

    companion object {
        fun fromValue(value: Int): BondState {
            for (type in values()) {
                if (type.value == value) {
                    return type
                }
            }
            return NONE
        }
    }

    init {
        this.value = value
    }
}