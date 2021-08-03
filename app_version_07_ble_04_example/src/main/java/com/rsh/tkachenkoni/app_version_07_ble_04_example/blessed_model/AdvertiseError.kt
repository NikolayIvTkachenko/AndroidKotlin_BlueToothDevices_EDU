package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model

import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS

/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
enum class AdvertiseError(value: Int) {
    /**
     * Failed to start advertising as the advertise data to be broadcasted is larger than 31 bytes.
     */
    DATA_TOO_LARGE(ADVERTISE_FAILED_DATA_TOO_LARGE),

    /**
     * Failed to start advertising because no advertising instance is available.
     */
    TOO_MANY_ADVERTISERS(ADVERTISE_FAILED_TOO_MANY_ADVERTISERS),

    /**
     * Failed to start advertising as the advertising is already started.
     */
    ALREADY_STARTED(ADVERTISE_FAILED_ALREADY_STARTED),

    /**
     * Operation failed due to an internal error.
     */
    INTERNAL_ERROR(ADVERTISE_FAILED_INTERNAL_ERROR),

    /**
     * This feature is not supported on this platform.
     */
    FEATURE_UNSUPPORTED(ADVERTISE_FAILED_FEATURE_UNSUPPORTED), UNKNOWN_ERROR(-1);

    val value: Int

    companion object {
        fun fromValue(value: Int): AdvertiseError {
            for (type in values()) {
                if (type.value == value) return type
            }
            return UNKNOWN_ERROR
        }
    }

    init {
        this.value = value
    }
}