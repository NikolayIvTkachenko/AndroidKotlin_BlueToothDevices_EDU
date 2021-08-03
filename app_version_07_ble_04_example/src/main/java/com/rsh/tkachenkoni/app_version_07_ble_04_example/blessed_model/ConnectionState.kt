package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model

/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
enum class ConnectionState(value: Int) {
    /**
     * The peripheral is disconnected
     */
    DISCONNECTED(0),

    /**
     * The peripheral is connecting
     */
    CONNECTING(1),

    /**
     * The peripheral is connected
     */
    CONNECTED(2),

    /**
     * The peripheral is disconnecting
     */
    DISCONNECTING(3);

    val value: Int

    companion object {
        fun fromValue(value: Int): ConnectionState {
            for (type in values()) {
                if (type.value == value) {
                    return type
                }
            }
            return DISCONNECTED
        }
    }

    init {
        this.value = value
    }
}