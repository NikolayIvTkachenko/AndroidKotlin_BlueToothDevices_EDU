package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model

import android.bluetooth.le.ScanResult

/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
abstract class BluetoothCentralManagerCallback {
    /**
     * Trying to connected with a peripheral.
     *
     * @param peripheral the peripheral that is connecting
     */
    fun onConnectingPeripheral(peripheral: BluetoothPeripheral) {}

    /**
     * Successfully connected with a peripheral.
     *
     * @param peripheral the peripheral that was connected.
     */
    open fun onConnectedPeripheral(peripheral: BluetoothPeripheral) {}

    /**
     * Connecting with the peripheral has failed.
     *
     * @param peripheral the peripheral for which the connection was attempted
     * @param status the status code for the connection failure
     */
    open fun onConnectionFailed(peripheral: BluetoothPeripheral, status: HciStatus) {}

    /**
     * Trying to disconnect peripheral
     *
     * @param peripheral the peripheral we are trying to disconnect
     */
    fun onDisconnectingPeripheral(peripheral: BluetoothPeripheral) {}

    /**
     * Peripheral disconnected
     *
     * @param peripheral the peripheral that disconnected.
     * @param status the status code for the disconnection
     */
    open fun onDisconnectedPeripheral(peripheral: BluetoothPeripheral, status: HciStatus) {}

    /**
     * Discovered a peripheral
     *
     * @param peripheral the peripheral that was found
     * @param scanResult the scanResult describing the peripheral
     */
    open fun onDiscoveredPeripheral(peripheral: BluetoothPeripheral, scanResult: ScanResult) {}

    /**
     * Scanning failed
     *
     * @param scanFailure the status code for the scanning failure
     */
    open fun onScanFailed(scanFailure: ScanFailure) {}

    /**
     * Bluetooth adapter status changed
     *
     * @param state the current status code for the adapter
     */
    open fun onBluetoothAdapterStateChanged(state: Int) {}
}
