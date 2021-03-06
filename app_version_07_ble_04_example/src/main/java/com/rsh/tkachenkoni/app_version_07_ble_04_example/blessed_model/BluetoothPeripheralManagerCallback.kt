package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.AdvertiseSettings

/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
abstract class BluetoothPeripheralManagerCallback {
    /**
     * Indicates whether a local service has been added successfully.
     *
     * @param status  Returns SUCCESS if the service was added
     * successfully.
     * @param service The service that has been added
     */
    fun onServiceAdded(status: GattStatus, service: BluetoothGattService) {}

    /**
     * A remote central has requested to read a local characteristic.
     *
     *
     * This callback is called before the current value of the characteristic is returned to the central.
     * Therefore, any modifications to the characteristic value can still be made.
     * If the characteristic's value is longer than the MTU - 1 bytes, a long read will be executed automatically
     *
     * @param bluetoothCentral the central that is doing the request
     * @param characteristic the characteristic to be read
     */
    fun onCharacteristicRead(
        bluetoothCentral: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
    }

    /**
     * A remote central has requested to write a local characteristic.
     *
     *
     * This callback is called before the current value of the characteristic is set to `value`.
     * The value should be checked and a GattStatus should be returned. If anything else than GattStatus.SUCCESS is returned,
     * the characteristic's value will not be updated.
     *
     *
     * The value may be up to 512 bytes (in case of a long write)
     *
     * @param bluetoothCentral the central that is doing the request
     * @param characteristic the characteristic to be written
     * @param value the value the central wants to write
     * @return GattStatus.SUCCESS if the value is acceptable, otherwise an appropriate status
     */
    fun onCharacteristicWrite(
        bluetoothCentral: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): GattStatus {
        return GattStatus.SUCCESS
    }

    /**
     * A remote central has requested to read a local descriptor.
     *
     *
     * This callback is called before the current value of the descriptor is returned to the central.
     * Therefore, any modifications to the characteristic value can still be made.
     * If the descriptor's value is longer than the MTU - 1 bytes, a long read will be executed automatically
     *
     * @param bluetoothCentral the central that is doing the request
     * @param descriptor the descriptor to be read
     */
    fun onDescriptorRead(bluetoothCentral: BluetoothCentral, descriptor: BluetoothGattDescriptor) {}

    /**
     * A remote central has requested to write a local descriptor.
     *
     *
     * This callback is called before the current value of the descriptor is set to `value`.
     * The value should be checked and a GattStatus should be returned. If anything else than GattStatus.SUCCESS is returned,
     * the descriptor's value will not be updated.
     *
     *
     * The value may be up to 512 bytes (in case of a long write)
     *
     * @param bluetoothCentral the central that is doing the request
     * @param descriptor the descriptor to be written
     * @param value the value the central wants to write
     * @return GattStatus.SUCCESS if the value is acceptable, otherwise an appropriate status
     */
    fun onDescriptorWrite(
        bluetoothCentral: BluetoothCentral,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): GattStatus {
        return GattStatus.SUCCESS
    }

    /**
     * A remote central has enabled notifications or indications for a characteristic
     *
     * @param bluetoothCentral the central
     * @param characteristic the characteristic
     */
    fun onNotifyingEnabled(
        bluetoothCentral: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
    }

    /**
     * A remote central has disabled notifications or indications for a characteristic
     *
     * @param bluetoothCentral the central
     * @param characteristic the characteristic
     */
    fun onNotifyingDisabled(
        bluetoothCentral: BluetoothCentral,
        characteristic: BluetoothGattCharacteristic
    ) {
    }

    /**
     * A notification has been sent to a central
     *
     * @param bluetoothCentral the central
     * @param value the value of the notification
     * @param characteristic the characteristic for which the notification was sent
     * @param status the status of the operation
     */
    fun onNotificationSent(
        bluetoothCentral: BluetoothCentral,
        value: ByteArray,
        characteristic: BluetoothGattCharacteristic,
        status: GattStatus
    ) {
    }

    /**
     * A remote central has connected
     *
     * @param bluetoothCentral the central
     */
    fun onCentralConnected(bluetoothCentral: BluetoothCentral) {}

    /**
     * A remote central has disconnected
     *
     * @param bluetoothCentral the central
     */
    fun onCentralDisconnected(bluetoothCentral: BluetoothCentral) {}

    /**
     * Advertising has successfully started
     *
     * @param settingsInEffect the AdvertiseSettings that are currently active
     */
    fun onAdvertisingStarted(settingsInEffect: AdvertiseSettings) {}

    /**
     * Advertising has failed
     *
     * @param advertiseError the error explaining why the advertising failed
     */
    fun onAdvertiseFailure(advertiseError: AdvertiseError) {}

    /**
     * Advertising has stopped
     *
     */
    fun onAdvertisingStopped() {}
}