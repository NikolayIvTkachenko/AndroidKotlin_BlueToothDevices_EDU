package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model

import java.util.*

/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
class BluetoothCentral internal constructor(address: String, name: String?) {
    private val address: String
    private val name: String?
    private var currentMtu = 23
    fun getAddress(): String {
        return address
    }

    fun getName(): String {
        return name ?: ""
    }

    fun setCurrentMtu(currentMtu: Int) {
        this.currentMtu = currentMtu
    }

    fun getCurrentMtu(): Int {
        return currentMtu
    }

    /**
     * Get maximum length of byte array that can be written depending on WriteType
     *
     * This value is derived from the current negotiated MTU or the maximum characteristic length (512)
     */
    fun getMaximumWriteValueLength(writeType: WriteType): Int {
        Objects.requireNonNull<Any>(writeType, "writetype is null")
        return when (writeType) {
            WriteType.WITH_RESPONSE -> 512
            WriteType.SIGNED -> currentMtu - 15
            else -> currentMtu - 3
        }
    }

    init {
        this.address = Objects.requireNonNull(address, "address is null")
        this.name = name
    }
}