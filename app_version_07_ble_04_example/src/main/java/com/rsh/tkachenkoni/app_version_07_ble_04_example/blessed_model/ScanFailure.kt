package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model

import android.bluetooth.le.ScanCallback.*


/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
enum class ScanFailure(value: Int) {
    /**
     * Failed to start scan as BLE scan with the same settings is already started by the app.
     */
    ALREADY_STARTED(SCAN_FAILED_ALREADY_STARTED),

    /**
     * Failed to start scan as app cannot be registered.
     */
    APPLICATION_REGISTRATION_FAILED(SCAN_FAILED_APPLICATION_REGISTRATION_FAILED),

    /**
     * Failed to start scan due an internal error
     */
    INTERNAL_ERROR(SCAN_FAILED_INTERNAL_ERROR),

    /**
     * Failed to start power optimized scan as this feature is not supported.
     */
    FEATURE_UNSUPPORTED(SCAN_FAILED_FEATURE_UNSUPPORTED),

    /**
     * Failed to start scan as it is out of hardware resources.
     */
    OUT_OF_HARDWARE_RESOURCES(5),

    /**
     * Failed to start scan as application tries to scan too frequently.
     */
    SCANNING_TOO_FREQUENTLY(6), UNKNOWN(-1);

    val value: Int

    companion object {
        fun fromValue(value: Int): ScanFailure {
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