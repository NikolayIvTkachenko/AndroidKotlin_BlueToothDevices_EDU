package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model

/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
enum class PhyOptions(value: Int) {
    /**
     * No preferred option. Use this value in combination with PHY_LE_1M and PHY_LE_2M
     */
    NO_PREFERRED(0),

    /**
     * Prefer 2x range option with throughput of +/- 500 Kbps
     */
    S2(1),

    /**
     * Prefer 4x range option with throughput of +/- 125 Kbps
     */
    S8(2);

    val value: Int

    init {
        this.value = value
    }
}