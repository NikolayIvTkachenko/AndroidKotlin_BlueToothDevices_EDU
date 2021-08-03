package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model

/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
enum class PhyType(value: Int, mask: Int) {
    /**
     * A Physical Layer (PHY) connection of 1 mbit. Compatible with Bluetooth 4.0, 4.1, 4.2 and 5.0
     */
    LE_1M(1, 1),

    /**
     * A Physical Layer (PHY) connection of 2 mbit. Requires Bluetooth 5
     */
    LE_2M(2, 2),

    /**
     * A Physical Layer (PHY) connection with long range. Requires Bluetooth 5
     */
    LE_CODED(3, 4),

    /**
     * Unknown Phy Type. Not to be used.
     */
    UNKNOWN_PHY_TYPE(-1, -1);

    val value: Int
    val mask: Int

    companion object {
        fun fromValue(value: Int): PhyType {
            for (type in values()) {
                if (type.value == value) return type
            }
            return UNKNOWN_PHY_TYPE
        }
    }

    init {
        this.value = value
        this.mask = mask
    }
}