package com.rsh.tkachenkoni.app_version_06_ble_03_sample

import java.util.*

/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
object CharacteristicValues {
    val HEART_RATE_MEASUREMENT          = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    val CSC_MEASUREMENT                 = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")
    val MANUFACTURER_STRING             = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
    val MODEL_NUMBER_STRING             = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    val FIRMWARE_REVISION_STRING        = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
    val APPEARANCE                      = UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb")
    val BODY_SENSOR_LOCATION            = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL                   = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    val CLIENT_CHARACTERISTIC_CONFIG    = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}