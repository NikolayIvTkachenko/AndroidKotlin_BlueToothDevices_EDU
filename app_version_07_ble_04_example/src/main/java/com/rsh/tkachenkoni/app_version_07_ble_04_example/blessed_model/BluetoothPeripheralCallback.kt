package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor

/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
abstract class BluetoothPeripheralCallback {
    /**
     * Callback invoked when the list of remote services, characteristics and descriptors
     * for the remote peripheral has been discovered.
     *
     */
    open fun onServicesDiscovered(peripheral: BluetoothPeripheral) {}

    /**
     * Callback invoked when the notification state of a characteristic has changed.
     *
     *
     * Use [BluetoothPeripheral.isNotifying] to get the current notification state of the characteristic
     *
     * @param peripheral the peripheral
     * @param characteristic the characteristic for which the notification state changed
     * @param status GATT status code
     */
    open fun onNotificationStateUpdate(
        peripheral: BluetoothPeripheral,
        characteristic: BluetoothGattCharacteristic,
        status: GattStatus
    ) {
    }

    /**
     * Callback invoked as the result of a characteristic read operation or notification/indication
     *
     * @param peripheral the peripheral
     * @param value the new value received
     * @param characteristic the characteristic for which the new value was received
     * @param status GATT status code
     */
    open fun onCharacteristicUpdate(
        peripheral: BluetoothPeripheral,
        value: ByteArray,
        characteristic: BluetoothGattCharacteristic,
        status: GattStatus
    ) {
    }

    /**
     * Callback invoked as the result of a characteristic write operation.
     *
     * @param peripheral the peripheral
     * @param value the value that was written
     * @param characteristic the characteristic written to
     * @param status GATT status code
     */
    open fun onCharacteristicWrite(
        peripheral: BluetoothPeripheral,
        value: ByteArray,
        characteristic: BluetoothGattCharacteristic,
        status: GattStatus
    ) {
    }

    /**
     * Callback invoked as the result of a descriptor read operation
     *
     * @param peripheral the peripheral
     * @param value the read value
     * @param descriptor the descriptor that was read
     * @param status GATT status code
     */
    fun onDescriptorRead(
        peripheral: BluetoothPeripheral,
        value: ByteArray,
        descriptor: BluetoothGattDescriptor,
        status: GattStatus
    ) {
    }

    /**
     * Callback invoked as the result of a descriptor write operation.
     * This callback is not called for the Client Characteristic Configuration descriptor. Instead the [BluetoothPeripheralCallback.onNotificationStateUpdate] will be called
     *
     * @param peripheral the peripheral
     * @param value the value that was written
     * @param descriptor the descriptor written to
     * @param status the GATT status code
     */
    fun onDescriptorWrite(
        peripheral: BluetoothPeripheral,
        value: ByteArray,
        descriptor: BluetoothGattDescriptor,
        status: GattStatus
    ) {
    }

    /**
     * Callback invoked when the bonding process is started
     *
     * @param peripheral the peripheral
     */
    fun onBondingStarted(peripheral: BluetoothPeripheral) {}

    /**
     * Callback invoked when the bonding process has succeeded
     *
     * @param peripheral the peripheral
     */
    fun onBondingSucceeded(peripheral: BluetoothPeripheral) {}

    /**
     * Callback invoked when the bonding process has failed
     *
     * @param peripheral the peripheral
     */
    fun onBondingFailed(peripheral: BluetoothPeripheral) {}

    /**
     * Callback invoked when a bond has been lost and the peripheral is not bonded anymore.
     *
     * @param peripheral the peripheral
     */
    fun onBondLost(peripheral: BluetoothPeripheral) {}

    /**
     * Callback invoked as the result of a read RSSI operation
     *
     * @param peripheral the peripheral
     * @param rssi the RSSI value
     * @param status GATT status code
     */
    fun onReadRemoteRssi(peripheral: BluetoothPeripheral, rssi: Int, status: GattStatus) {}

    /**
     * Callback invoked as the result of a MTU request operation
     *
     * @param peripheral the peripheral
     * @param mtu the new MTU
     * @param status GATT status code
     */
    open fun onMtuChanged(peripheral: BluetoothPeripheral, mtu: Int, status: GattStatus) {}

    /**
     * Callback invoke as result of readPhy or setPhy operation
     *
     * @param peripheral the peripheral
     * @param txPhy the transmitter PHY in use.
     * @param rxPhy the receiver PHY in use
     * @param status GATT status code
     */
    fun onPhyUpdate(
        peripheral: BluetoothPeripheral,
        txPhy: PhyType,
        rxPhy: PhyType,
        status: GattStatus
    ) {
    }

    /**
     * Callback invoked when the connection parameters are updated.
     *
     * This can happen as a result of requestConnectionPriority() or when the stack/peripheral decides to change the connection parameters.
     * This callback is only called for Android 8 (Oreo) or newer.
     *
     * @param peripheral the peripheral
     * @param interval Connection interval used on this connection, 1.25ms unit.
     * Valid range is from 6 (7.5ms) to 3200 (4000ms).
     * @param latency  Slave latency for the connection in number of connection events.
     * Valid range is from 0 to 499.
     * @param timeout  Supervision timeout for this connection, in 10ms unit.
     * Valid range is from 10 (0.1s) to 3200 (32s).
     * @param status GATT status code
     */
    fun onConnectionUpdated(
        peripheral: BluetoothPeripheral,
        interval: Int,
        latency: Int,
        timeout: Int,
        status: GattStatus
    ) {
    }

    /**
     * NULL class to deal with nullability
     */
    internal class NULL : BluetoothPeripheralCallback()
}