package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
@SuppressWarnings({"SpellCheckingInspection", "WeakerAccess", "UnusedReturnValue"})
class BluetoothCentralManager(
    context: Context,
    bluetoothCentralManagerCallback: BluetoothCentralManagerCallback,
    handler: Handler
) {
    private val context: Context
    private val callBackHandler: Handler
    private val bluetoothAdapter: BluetoothAdapter
    private var bluetoothScanner: BluetoothLeScanner? = null
    private var autoConnectScanner: BluetoothLeScanner? = null
    private val bluetoothCentralManagerCallback: BluetoothCentralManagerCallback
    private val connectedPeripherals: MutableMap<String, BluetoothPeripheral> =
        ConcurrentHashMap<String, BluetoothPeripheral>()
    private val unconnectedPeripherals: MutableMap<String, BluetoothPeripheral?> =
        ConcurrentHashMap<String, BluetoothPeripheral?>()
    private val scannedPeripherals: MutableMap<String, BluetoothPeripheral> =
        ConcurrentHashMap<String, BluetoothPeripheral>()
    private val reconnectPeripheralAddresses: MutableList<String> = ArrayList()
    private val reconnectCallbacks: MutableMap<String, BluetoothPeripheralCallback?> =
        ConcurrentHashMap<String, BluetoothPeripheralCallback?>()
    private var scanPeripheralNames = arrayOfNulls<String>(0)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private var autoConnectRunnable: Runnable? = null
    private val connectLock = Any()
    private var currentCallback: ScanCallback? = null
    private var currentFilters: kotlin.collections.List<ScanFilter>? = null
    private var scanSettings: ScanSettings
    private val autoConnectScanSettings: ScanSettings
    private val connectionRetries: MutableMap<String, Int> = ConcurrentHashMap()
    private var expectingBluetoothOffDisconnects = false
    private var disconnectRunnable: Runnable? = null
    private val pinCodes: MutableMap<String, String> = ConcurrentHashMap()
    private var transport: Transport = DEFAULT_TRANSPORT

    //region Callbacks
    private val scanByNameCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            synchronized(this) {
                val deviceName = result.device.name ?: return
                for (name in scanPeripheralNames) {
                    if (deviceName.contains(name!!)) {
                        sendScanResult(result)
                        return
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            sendScanFailed(ScanFailure.fromValue(errorCode))
        }
    }
    private val defaultScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            synchronized(this) { sendScanResult(result) }
        }

        override fun onScanFailed(errorCode: Int) {
            sendScanFailed(ScanFailure.fromValue(errorCode))
        }
    }

    private fun sendScanResult(result: ScanResult) {
        callBackHandler.post {
            if (isScanning()) {
                val peripheral: BluetoothPeripheral = getPeripheral(result.device.address)
                peripheral.setDevice(result.device)
                bluetoothCentralManagerCallback.onDiscoveredPeripheral(peripheral, result)
            }
        }
    }

    private fun sendScanFailed(scanFailure: ScanFailure) {
        currentCallback = null
        currentFilters = null
        callBackHandler.post {
            Logger.e(
                TAG,
                "scan failed with error code %d (%s)",
                scanFailure.value,
                scanFailure
            )
            bluetoothCentralManagerCallback.onScanFailed(scanFailure)
        }
    }

    private val autoConnectScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            synchronized(this) {
                if (!isAutoScanning()) return
                Logger.d(
                    TAG,
                    "peripheral with address '%s' found",
                    result.device.address
                )
                stopAutoconnectScan()
                val deviceAddress = result.device.address
                val peripheral: BluetoothPeripheral? = unconnectedPeripherals[deviceAddress]
                val callback: BluetoothPeripheralCallback? = reconnectCallbacks[deviceAddress]
                reconnectPeripheralAddresses.remove(deviceAddress)
                reconnectCallbacks.remove(deviceAddress)
                unconnectedPeripherals.remove(deviceAddress)
                scannedPeripherals.remove(deviceAddress)
                if (peripheral != null && callback != null) {
                    connectPeripheral(peripheral, callback)
                }
                if (reconnectPeripheralAddresses.size > 0) {
                    scanForAutoConnectPeripherals()
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val scanFailure: ScanFailure = ScanFailure.fromValue(errorCode)
            Logger.e(TAG, "autoConnect scan failed with error code %d (%s)", errorCode, scanFailure)
            autoConnectScanner = null
            callBackHandler.post { bluetoothCentralManagerCallback.onScanFailed(scanFailure) }
        }
    }
    protected val internalCallback: BluetoothPeripheral.InternalCallback =
        object : BluetoothPeripheral.InternalCallback() {
            override fun connecting(peripheral: BluetoothPeripheral) {
                callBackHandler.post {
                    bluetoothCentralManagerCallback.onConnectingPeripheral(
                        peripheral
                    )
                }
            }

            override fun connected(peripheral: BluetoothPeripheral) {
                connectionRetries.remove(peripheral.getAddress())
                unconnectedPeripherals.remove(peripheral.getAddress())
                scannedPeripherals.remove(peripheral.getAddress())
                connectedPeripherals[peripheral.getAddress()] = peripheral
                callBackHandler.post {
                    bluetoothCentralManagerCallback.onConnectedPeripheral(
                        peripheral
                    )
                }
            }

            override fun connectFailed(peripheral: BluetoothPeripheral, status: HciStatus) {
                unconnectedPeripherals.remove(peripheral.getAddress())
                scannedPeripherals.remove(peripheral.getAddress())

                // Get the number of retries for this peripheral
                var nrRetries = 0
                val retries = connectionRetries[peripheral.getAddress()]
                if (retries != null) nrRetries = retries

                // Retry connection or conclude the connection has failed
                if (nrRetries < MAX_CONNECTION_RETRIES && status !== HciStatus.CONNECTION_FAILED_ESTABLISHMENT) {
                    Logger.i(
                        TAG,
                        "retrying connection to '%s' (%s)",
                        peripheral.getName(),
                        peripheral.getAddress()
                    )
                    nrRetries++
                    connectionRetries[peripheral.getAddress()] = nrRetries
                    unconnectedPeripherals[peripheral.getAddress()] = peripheral
                    peripheral.connect()
                } else {
                    Logger.i(
                        TAG,
                        "connection to '%s' (%s) failed",
                        peripheral.getName(),
                        peripheral.getAddress()
                    )
                    connectionRetries.remove(peripheral.getAddress())
                    callBackHandler.post {
                        bluetoothCentralManagerCallback.onConnectionFailed(
                            peripheral,
                            status
                        )
                    }
                }
            }

            override fun disconnecting(peripheral: BluetoothPeripheral) {
                callBackHandler.post {
                    bluetoothCentralManagerCallback.onDisconnectingPeripheral(
                        peripheral
                    )
                }
            }

            override fun disconnected(peripheral: BluetoothPeripheral, status: HciStatus) {
                if (expectingBluetoothOffDisconnects) {
                    cancelDisconnectionTimer()
                    expectingBluetoothOffDisconnects = false
                }
                connectedPeripherals.remove(peripheral.getAddress())
                unconnectedPeripherals.remove(peripheral.getAddress())
                scannedPeripherals.remove(peripheral.getAddress())
                connectionRetries.remove(peripheral.getAddress())
                callBackHandler.post {
                    bluetoothCentralManagerCallback.onDisconnectedPeripheral(
                        peripheral,
                        status
                    )
                }
            }

            override fun getPincode(peripheral: BluetoothPeripheral): String? {
                return pinCodes[peripheral.getAddress()]
            }
        }

    /**
     * Closes BluetoothCentralManager and cleans up internals. BluetoothCentralManager will not work anymore after this is called.
     */
    fun close() {
        unconnectedPeripherals.clear()
        connectedPeripherals.clear()
        reconnectCallbacks.clear()
        reconnectPeripheralAddresses.clear()
        scannedPeripherals.clear()
        context.unregisterReceiver(adapterStateReceiver)
    }

    /**
     * Enable logging
     */
    fun enableLogging() {
        Logger.enabled = true
    }

    /**
     * Disable logging
     */
    fun disableLogging() {
        Logger.enabled = false
    }

    private fun getScanSettings(scanMode: ScanMode): ScanSettings {
        Objects.requireNonNull<Any>(scanMode, "scanMode is null")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ScanSettings.Builder()
                .setScanMode(scanMode.value)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                .setReportDelay(0L)
                .build()
        } else {
            ScanSettings.Builder()
                .setScanMode(scanMode.value)
                .setReportDelay(0L)
                .build()
        }
    }

    /**
     * Set the default scanMode.
     *
     * @param scanMode the scanMode to set
     */
    fun setScanMode(scanMode: ScanMode) {
        Objects.requireNonNull<Any>(scanMode)
        scanSettings = getScanSettings(scanMode)
    }

    /**
     * Get the transport to be used during connection phase.
     *
     * @return transport
     */
    fun getTransport(): Transport {
        return transport
    }

    /**
     * Set the transport to be used when creating instances of [BluetoothPeripheral].
     *
     * @param transport the Transport to set
     */
    fun setTransport(transport: Transport) {
        this.transport = transport
    }

    private fun startScan(
        filters: kotlin.collections.List<ScanFilter>,
        scanSettings: ScanSettings,
        scanCallback: ScanCallback
    ) {
        if (bleNotReady()) return
        if (isScanning()) {
            Logger.e(TAG, "other scan still active, stopping scan")
            stopScan()
        }
        if (bluetoothScanner == null) {
            bluetoothScanner = bluetoothAdapter.bluetoothLeScanner
        }
        if (bluetoothScanner != null) {
            setScanTimer()
            currentCallback = scanCallback
            currentFilters = filters
            bluetoothScanner!!.startScan(filters, scanSettings, scanCallback)
            Logger.i(TAG, "scan started")
        } else {
            Logger.e(TAG, "starting scan failed")
        }
    }

    /**
     * Scan for peripherals that advertise at least one of the specified service UUIDs.
     *
     * @param serviceUUIDs an array of service UUIDs
     */
    fun scanForPeripheralsWithServices(serviceUUIDs: Array<UUID>) {
        Objects.requireNonNull(serviceUUIDs, "no service UUIDs supplied")
        require(serviceUUIDs.size != 0) { "at least one service UUID  must be supplied" }
        val filters: MutableList<ScanFilter> = ArrayList()
        for (serviceUUID in serviceUUIDs) {
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUUID))
                .build()
            filters.add(filter)
        }
        startScan(filters, scanSettings, defaultScanCallback)
    }

    /**
     * Scan for peripherals with advertisement names containing any of the specified peripheral names.
     *
     *
     * Substring matching is used so only a partial peripheral names has to be supplied.
     *
     * @param peripheralNames array of partial peripheral names
     */
    fun scanForPeripheralsWithNames(peripheralNames: Array<String?>) {
        Objects.requireNonNull(peripheralNames, "no peripheral names supplied")
        require(peripheralNames.size != 0) { "at least one peripheral name must be supplied" }

        // Start the scanner with no filter because we'll do the filtering ourselves
        scanPeripheralNames = peripheralNames
        startScan(emptyList(), scanSettings, scanByNameCallback)
    }

    /**
     * Scan for peripherals that have any of the specified peripheral mac addresses.
     *
     * @param peripheralAddresses array of peripheral mac addresses to scan for
     */
    fun scanForPeripheralsWithAddresses(peripheralAddresses: Array<String>) {
        Objects.requireNonNull(peripheralAddresses, "no peripheral addresses supplied")
        require(peripheralAddresses.size != 0) { "at least one peripheral address must be supplied" }
        val filters: MutableList<ScanFilter> = ArrayList()
        for (address in peripheralAddresses) {
            if (BluetoothAdapter.checkBluetoothAddress(address)) {
                val filter = ScanFilter.Builder()
                    .setDeviceAddress(address)
                    .build()
                filters.add(filter)
            } else {
                Logger.e(
                    TAG,
                    "%s is not a valid address. Make sure all alphabetic characters are uppercase.",
                    address
                )
            }
        }
        startScan(filters, scanSettings, defaultScanCallback)
    }

    /**
     * Scan for any peripheral that matches the supplied filters
     *
     * @param filters A list of ScanFilters
     */
    fun scanForPeripheralsUsingFilters(filters: kotlin.collections.List<ScanFilter>) {
        Objects.requireNonNull(filters, "no filters supplied")
        require(!filters.isEmpty()) { "at least one scan filter must be supplied" }
        startScan(filters, scanSettings, defaultScanCallback)
    }

    /**
     * Scan for any peripheral that is advertising.
     */
    fun scanForPeripherals() {
        startScan(emptyList(), scanSettings, defaultScanCallback)
    }

    /**
     * Scan for peripherals that need to be autoconnected but are not cached
     */
    private fun scanForAutoConnectPeripherals() {
        if (bleNotReady()) return
        if (autoConnectScanner != null) {
            stopAutoconnectScan()
        }
        autoConnectScanner = bluetoothAdapter.bluetoothLeScanner
        if (autoConnectScanner != null) {
            val filters: MutableList<ScanFilter> = ArrayList()
            for (address in reconnectPeripheralAddresses) {
                val filter = ScanFilter.Builder()
                    .setDeviceAddress(address)
                    .build()
                filters.add(filter)
            }
            autoConnectScanner!!.startScan(
                filters,
                autoConnectScanSettings,
                autoConnectScanCallback
            )
            Logger.d(
                TAG,
                "started scanning to autoconnect peripherals (" + reconnectPeripheralAddresses.size + ")"
            )
            setAutoConnectTimer()
        } else {
            Logger.e(TAG, "starting autoconnect scan failed")
        }
    }

    private fun stopAutoconnectScan() {
        cancelAutoConnectTimer()
        if (autoConnectScanner != null) {
            autoConnectScanner!!.stopScan(autoConnectScanCallback)
            autoConnectScanner = null
            Logger.i(TAG, "autoscan stopped")
        }
    }

    private fun isAutoScanning(): Boolean {
        return autoConnectScanner != null
    }

    /**
     * Stop scanning for peripherals.
     */
    fun stopScan() {
        cancelTimeoutTimer()
        if (isScanning()) {
            if (bluetoothScanner != null) {
                bluetoothScanner!!.stopScan(currentCallback)
                Logger.i(TAG, "scan stopped")
            }
        } else {
            Logger.i(TAG, "no scan to stop because no scan is running")
        }
        currentCallback = null
        currentFilters = null
        scannedPeripherals.clear()
    }

    /**
     * Check if a scanning is active
     *
     * @return true if a scan is active, otherwise false
     */
    fun isScanning(): Boolean {
        return bluetoothScanner != null && currentCallback != null
    }

    /**
     * Connect to a known peripheral immediately. The peripheral must have been found by scanning for this call to succeed. This method will time out in max 30 seconds on most phones and in 5 seconds on Samsung phones.
     * If the peripheral is already connected, no connection attempt will be made. This method is asynchronous and there can be only one outstanding connect.
     *
     * @param peripheral BLE peripheral to connect with
     */
    fun connectPeripheral(
        peripheral: BluetoothPeripheral,
        peripheralCallback: BluetoothPeripheralCallback
    ) {
        synchronized(connectLock) {
            Objects.requireNonNull<Any>(
                peripheral,
                NO_VALID_PERIPHERAL_PROVIDED
            )
            Objects.requireNonNull<Any>(
                peripheralCallback,
                NO_VALID_PERIPHERAL_CALLBACK_SPECIFIED
            )
            if (connectedPeripherals.containsKey(peripheral.getAddress())) {
                Logger.w(
                    TAG,
                    "already connected to %s'",
                    peripheral.getAddress()
                )
                return
            }
            if (unconnectedPeripherals.containsKey(peripheral.getAddress())) {
                Logger.w(
                    TAG,
                    "already connecting to %s'",
                    peripheral.getAddress()
                )
                return
            }

            // Check if the peripheral is cached or not. If not, issue a warning because connection may fail
            // This is because Android will guess the address type and when incorrect it will fail
            if (peripheral.isUncached()) {
                Logger.w(
                    TAG,
                    "peripheral with address '%s' is not in the Bluetooth cache, hence connection may fail",
                    peripheral.getAddress()
                )
            }
            peripheral.setPeripheralCallback(peripheralCallback)
            scannedPeripherals.remove(peripheral.getAddress())
            unconnectedPeripherals[peripheral.getAddress()] = peripheral
            peripheral.connect()
        }
    }

    /**
     * Automatically connect to a peripheral when it is advertising. It is not necessary to scan for the peripheral first. This call is asynchronous and will not time out.
     *
     * @param peripheral the peripheral
     */
    fun autoConnectPeripheral(
        peripheral: BluetoothPeripheral,
        peripheralCallback: BluetoothPeripheralCallback
    ) {
        synchronized(connectLock) {
            Objects.requireNonNull<Any>(
                peripheral,
                NO_VALID_PERIPHERAL_PROVIDED
            )
            Objects.requireNonNull<Any>(
                peripheralCallback,
                NO_VALID_PERIPHERAL_CALLBACK_SPECIFIED
            )
            if (connectedPeripherals.containsKey(peripheral.getAddress())) {
                Logger.w(
                    TAG,
                    "already connected to %s'",
                    peripheral.getAddress()
                )
                return
            }
            if (unconnectedPeripherals[peripheral.getAddress()] != null) {
                Logger.w(
                    TAG,
                    "already issued autoconnect for '%s' ",
                    peripheral.getAddress()
                )
                return
            }

            // Check if the peripheral is uncached and start autoConnectPeripheralByScan
            if (peripheral.isUncached()) {
                Logger.d(
                    TAG,
                    "peripheral with address '%s' not in Bluetooth cache, autoconnecting by scanning",
                    peripheral.getAddress()
                )
                scannedPeripherals.remove(peripheral.getAddress())
                unconnectedPeripherals[peripheral.getAddress()] = peripheral
                autoConnectPeripheralByScan(peripheral.getAddress(), peripheralCallback)
                return
            }
            if (peripheral.getType() === PeripheralType.CLASSIC) {
                Logger.e(
                    TAG,
                    "peripheral does not support Bluetooth LE"
                )
                return
            }
            peripheral.setPeripheralCallback(peripheralCallback)
            scannedPeripherals.remove(peripheral.getAddress())
            unconnectedPeripherals[peripheral.getAddress()] = peripheral
            peripheral.autoConnect()
        }
    }

    private fun autoConnectPeripheralByScan(
        peripheralAddress: String,
        peripheralCallback: BluetoothPeripheralCallback
    ) {
        if (reconnectPeripheralAddresses.contains(peripheralAddress)) {
            Logger.w(TAG, "peripheral already on list for reconnection")
            return
        }
        reconnectPeripheralAddresses.add(peripheralAddress)
        reconnectCallbacks[peripheralAddress] = peripheralCallback
        scanForAutoConnectPeripherals()
    }

    /**
     * Cancel an active or pending connection for a peripheral.
     *
     * @param peripheral the peripheral
     */
    fun cancelConnection(peripheral: BluetoothPeripheral) {
        Objects.requireNonNull<Any>(peripheral, NO_VALID_PERIPHERAL_PROVIDED)

        // First check if we are doing a reconnection scan for this peripheral
        val peripheralAddress: String = peripheral.getAddress()
        if (reconnectPeripheralAddresses.contains(peripheralAddress)) {
            reconnectPeripheralAddresses.remove(peripheralAddress)
            reconnectCallbacks.remove(peripheralAddress)
            unconnectedPeripherals.remove(peripheralAddress)
            stopAutoconnectScan()
            Logger.d(TAG, "cancelling autoconnect for %s", peripheralAddress)
            callBackHandler.post {
                bluetoothCentralManagerCallback.onDisconnectedPeripheral(
                    peripheral,
                    HciStatus.SUCCESS
                )
            }

            // If there are any devices left, restart the reconnection scan
            if (reconnectPeripheralAddresses.size > 0) {
                scanForAutoConnectPeripherals()
            }
            return
        }

        // Only cancel connectioins if it is an known peripheral
        if (unconnectedPeripherals.containsKey(peripheralAddress) || connectedPeripherals.containsKey(
                peripheralAddress
            )
        ) {
            peripheral.cancelConnection()
        } else {
            Logger.e(TAG, "cannot cancel connection to unknown peripheral %s", peripheralAddress)
        }
    }

    /**
     * Autoconnect to a batch of peripherals.
     *
     *
     * Use this function to autoConnect to a batch of peripherals, instead of calling autoConnect on each of them.
     * Calling autoConnect on many peripherals may cause Android scanning limits to kick in, which is avoided by using autoConnectPeripheralsBatch.
     *
     * @param batch the map of peripherals and their callbacks to autoconnect to
     */
    fun autoConnectPeripheralsBatch(batch: kotlin.collections.Map<BluetoothPeripheral, BluetoothPeripheralCallback>) {
        Objects.requireNonNull<kotlin.collections.Map<BluetoothPeripheral, BluetoothPeripheralCallback>>(
            batch,
            "no valid batch provided"
        )

        // Find the uncached peripherals and issue autoConnectPeripheral for the cached ones
        val uncachedPeripherals: MutableMap<BluetoothPeripheral, BluetoothPeripheralCallback?> =
            HashMap<BluetoothPeripheral, BluetoothPeripheralCallback?>()
        for (peripheral in batch.keys) {
            if (peripheral.isUncached()) {
                uncachedPeripherals[peripheral] = batch[peripheral]
            } else {
                batch[peripheral]?.let { autoConnectPeripheral(peripheral, it) }
            }
        }

        // Add uncached peripherals to list of peripherals to scan for
        if (!uncachedPeripherals.isEmpty()) {
            for (peripheral in uncachedPeripherals.keys) {
                val peripheralAddress: String = peripheral.getAddress()
                reconnectPeripheralAddresses.add(peripheralAddress)
                reconnectCallbacks[peripheralAddress] = uncachedPeripherals[peripheral]
                unconnectedPeripherals[peripheralAddress] = peripheral
            }
            scanForAutoConnectPeripherals()
        }
    }

    /**
     * Get a peripheral object matching the specified mac address.
     *
     * @param peripheralAddress mac address
     * @return a BluetoothPeripheral object matching the specified mac address or null if it was not found
     */
    fun getPeripheral(peripheralAddress: String): BluetoothPeripheral {
        Objects.requireNonNull(peripheralAddress, NO_PERIPHERAL_ADDRESS_PROVIDED)
        if (!BluetoothAdapter.checkBluetoothAddress(peripheralAddress)) {
            val message = String.format(
                "%s is not a valid bluetooth address. Make sure all alphabetic characters are uppercase.",
                peripheralAddress
            )
            throw IllegalArgumentException(message)
        }
        return if (connectedPeripherals.containsKey(peripheralAddress)) {
            Objects.requireNonNull(connectedPeripherals[peripheralAddress])
        } else if (unconnectedPeripherals.containsKey(peripheralAddress)) {
            Objects.requireNonNull(unconnectedPeripherals[peripheralAddress])
        } else if (scannedPeripherals.containsKey(peripheralAddress)) {
            Objects.requireNonNull(scannedPeripherals[peripheralAddress])
        } else {
            val peripheral = BluetoothPeripheral(
                context,
                bluetoothAdapter.getRemoteDevice(peripheralAddress),
                internalCallback,
                NULL(),
                callBackHandler,
                transport
            )
            scannedPeripherals[peripheralAddress] = peripheral
            peripheral
        }
    }

    /**
     * Get the list of connected peripherals.
     *
     * @return list of connected peripherals
     */
    fun getConnectedPeripherals(): Any? {
        return ArrayList<Any?>(connectedPeripherals.values)
    }

    private fun bleNotReady(): Boolean {
        if (isBleSupported()) {
            if (isBluetoothEnabled()) {
                return !permissionsGranted()
            }
        }
        return true
    }

    private fun isBleSupported(): Boolean {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return true
        }
        Logger.e(TAG, "BLE not supported")
        return false
    }

    /**
     * Check if Bluetooth is enabled
     *
     * @return true is Bluetooth is enabled, otherwise false
     */
    fun isBluetoothEnabled(): Boolean {
        if (bluetoothAdapter.isEnabled) {
            return true
        }
        Logger.e(TAG, "Bluetooth disabled")
        return false
    }

    private fun permissionsGranted(): Boolean {
        val targetSdkVersion = context.applicationInfo.targetSdkVersion
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("app does not have ACCESS_FINE_LOCATION permission, cannot start scan")
            } else true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("app does not have ACCESS_COARSE_LOCATION permission, cannot start scan")
            } else true
        } else {
            true
        }
    }

    /**
     * Set scan timeout timer, timeout time is `SCAN_TIMEOUT`.
     * If timeout is executed the scan is stopped and automatically restarted. This is done to avoid Android 9 scan restrictions
     */
    private fun setScanTimer() {
        cancelTimeoutTimer()
        timeoutRunnable = Runnable {
            Logger.d(TAG, "scanning timeout, restarting scan")
            val callback = currentCallback
            val filters = currentFilters
            stopScan()

            // Restart the scan and timer
            callBackHandler.postDelayed({
                if (callback != null) {
                    startScan(filters!!, scanSettings, callback)
                }
            }, SCAN_RESTART_DELAY.toLong())
        }
        mainHandler.postDelayed(timeoutRunnable!!, SCAN_TIMEOUT)
    }

    /**
     * Cancel the scan timeout timer
     */
    private fun cancelTimeoutTimer() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable!!)
            timeoutRunnable = null
        }
    }

    /**
     * Set scan timeout timer, timeout time is `SCAN_TIMEOUT`.
     * If timeout is executed the scan is stopped and automatically restarted. This is done to avoid Android 9 scan restrictions
     */
    private fun setAutoConnectTimer() {
        cancelAutoConnectTimer()
        autoConnectRunnable = Runnable {
            Logger.d(TAG, "autoconnect scan timeout, restarting scan")

            // Stop previous autoconnect scans if any
            if (autoConnectScanner != null) {
                autoConnectScanner!!.stopScan(autoConnectScanCallback)
                autoConnectScanner = null
            }

            // Restart the auto connect scan and timer
            mainHandler.postDelayed(
                { scanForAutoConnectPeripherals() },
                SCAN_RESTART_DELAY.toLong()
            )
        }
        mainHandler.postDelayed(autoConnectRunnable!!, SCAN_TIMEOUT)
    }

    /**
     * Cancel the scan timeout timer
     */
    private fun cancelAutoConnectTimer() {
        if (autoConnectRunnable != null) {
            mainHandler.removeCallbacks(autoConnectRunnable!!)
            autoConnectRunnable = null
        }
    }

    /**
     * Set a fixed PIN code for a peripheral that asks for a PIN code during bonding.
     *
     *
     * This PIN code will be used to programmatically bond with the peripheral when it asks for a PIN code. The normal PIN popup will not appear anymore.
     *
     * Note that this only works for peripherals with a fixed PIN code.
     *
     * @param peripheralAddress the address of the peripheral
     * @param pin               the 6 digit PIN code as a string, e.g. "123456"
     * @return true if the pin code and peripheral address are valid and stored internally
     */
    fun setPinCodeForPeripheral(peripheralAddress: String, pin: String): Boolean {
        Objects.requireNonNull(peripheralAddress, NO_PERIPHERAL_ADDRESS_PROVIDED)
        Objects.requireNonNull(pin, "no pin provided")
        if (!BluetoothAdapter.checkBluetoothAddress(peripheralAddress)) {
            Logger.e(
                TAG,
                "%s is not a valid address. Make sure all alphabetic characters are uppercase.",
                peripheralAddress
            )
            return false
        }
        if (pin.length != 6) {
            Logger.e(TAG, "%s is not 6 digits long", pin)
            return false
        }
        pinCodes[peripheralAddress] = pin
        return true
    }

    /**
     * Remove bond for a peripheral.
     *
     * @param peripheralAddress the address of the peripheral
     * @return true if the peripheral was succesfully bonded or it wasn't bonded, false if it was bonded and removing it failed
     */
    fun removeBond(peripheralAddress: String): Boolean {
        Objects.requireNonNull(peripheralAddress, NO_PERIPHERAL_ADDRESS_PROVIDED)

        // Get the set of bonded devices
        val bondedDevices = bluetoothAdapter.bondedDevices

        // See if the device is bonded
        var peripheralToUnBond: BluetoothDevice? = null
        if (bondedDevices.size > 0) {
            for (device in bondedDevices) {
                if (device.address == peripheralAddress) {
                    peripheralToUnBond = device
                }
            }
        } else {
            return true
        }

        // Try to remove the bond
        return if (peripheralToUnBond != null) {
            try {
                val method =
                    peripheralToUnBond.javaClass.getMethod("removeBond", *null as Array<Class<*>?>?)
                val result = method.invoke(peripheralToUnBond, *null as Array<Any?>?) as Boolean
                if (result) {
                    Logger.i(TAG, "Succesfully removed bond for '%s'", peripheralToUnBond.name)
                }
                result
            } catch (e: Exception) {
                Logger.i(TAG, "could not remove bond")
                e.printStackTrace()
                false
            }
        } else {
            true
        }
    }

    /**
     * Make the pairing popup appear in the foreground by doing a 1 sec discovery.
     *
     *
     * If the pairing popup is shown within 60 seconds, it will be shown in the foreground.
     */
    fun startPairingPopupHack() {
        // Check if we are on a Samsung device because those don't need the hack
        val manufacturer = Build.MANUFACTURER
        if (!manufacturer.equals("samsung", ignoreCase = true)) {
            bluetoothAdapter.startDiscovery()
            callBackHandler.postDelayed({
                Logger.d(TAG, "popup hack completed")
                bluetoothAdapter.cancelDiscovery()
            }, 1000)
        }
    }

    /**
     * Some phones, like Google/Pixel phones, don't automatically disconnect devices so this method does it manually
     */
    private fun cancelAllConnectionsWhenBluetoothOff() {
        Logger.d(TAG, "disconnect all peripherals because bluetooth is off")
        // Call cancelConnection for connected peripherals
        for (peripheral in connectedPeripherals.values) {
            peripheral.disconnectWhenBluetoothOff()
        }
        connectedPeripherals.clear()

        // Call cancelConnection for unconnected peripherals
        for (peripheral in unconnectedPeripherals.values) {
            peripheral.disconnectWhenBluetoothOff()
        }
        unconnectedPeripherals.clear()

        // Clean up autoconnect by scanning information
        reconnectPeripheralAddresses.clear()
        reconnectCallbacks.clear()
    }

    /**
     * Timer to determine if manual disconnection in case of bluetooth off is needed
     */
    private fun startDisconnectionTimer() {
        cancelDisconnectionTimer()
        disconnectRunnable = Runnable {
            Logger.e(
                TAG,
                "bluetooth turned off but no automatic disconnects happening, so doing it ourselves"
            )
            cancelAllConnectionsWhenBluetoothOff()
            disconnectRunnable = null
        }
        mainHandler.postDelayed(disconnectRunnable, 1000)
    }

    /**
     * Cancel timer for bluetooth off disconnects
     */
    private fun cancelDisconnectionTimer() {
        if (disconnectRunnable != null) {
            mainHandler.removeCallbacks(disconnectRunnable!!)
            disconnectRunnable = null
        }
    }

    protected val adapterStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                callBackHandler.post {
                    bluetoothCentralManagerCallback.onBluetoothAdapterStateChanged(
                        state
                    )
                }
                handleAdapterState(state)
            }
        }
    }

    private fun handleAdapterState(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_OFF -> {
                // Check if there are any connected peripherals or connections in progress
                if (connectedPeripherals.size > 0 || unconnectedPeripherals.size > 0) {
                    // See if they are automatically disconnect
                    expectingBluetoothOffDisconnects = true
                    startDisconnectionTimer()
                }
                Logger.d(TAG, "bluetooth turned off")
            }
            BluetoothAdapter.STATE_TURNING_OFF -> {
                expectingBluetoothOffDisconnects = true

                // Stop all scans so that we are back in a clean state
                // Note that we can't call stopScan if the adapter is off
                cancelTimeoutTimer()
                cancelAutoConnectTimer()
                currentCallback = null
                currentFilters = null
                autoConnectScanner = null
                Logger.d(TAG, "bluetooth turning off")
            }
            BluetoothAdapter.STATE_ON -> {
                expectingBluetoothOffDisconnects = false
                Logger.d(TAG, "bluetooth turned on")
            }
            BluetoothAdapter.STATE_TURNING_ON -> {
                expectingBluetoothOffDisconnects = false
                Logger.d(TAG, "bluetooth turning on")
            }
        }
    }

    companion object {
        private val TAG = BluetoothCentralManager::class.java.simpleName
        private const val SCAN_TIMEOUT = 180000L
        private const val SCAN_RESTART_DELAY = 1000
        private const val MAX_CONNECTION_RETRIES = 1
        private val DEFAULT_TRANSPORT: Transport = Transport.LE
        private const val NO_PERIPHERAL_ADDRESS_PROVIDED = "no peripheral address provided"
        private const val NO_VALID_PERIPHERAL_PROVIDED = "no valid peripheral provided"
        private const val NO_VALID_PERIPHERAL_CALLBACK_SPECIFIED =
            "no valid peripheral callback specified"
    }
    //endregion
    /**
     * Construct a new BluetoothCentralManager object
     *
     * @param context                  Android application environment.
     * @param bluetoothCentralManagerCallback the callback to call for updates
     * @param handler                  Handler to use for callbacks.
     */
    init {
        this.context = Objects.requireNonNull(context, "no valid context provided")
        this.bluetoothCentralManagerCallback = Objects.requireNonNull(
            bluetoothCentralManagerCallback,
            "no valid bluetoothCallback provided"
        )
        callBackHandler = Objects.requireNonNull(handler, "no valid handler provided")
        bluetoothAdapter = Objects.requireNonNull(
            BluetoothAdapter.getDefaultAdapter(),
            "no bluetooth adapter found"
        )
        autoConnectScanSettings = getScanSettings(ScanMode.LOW_POWER)
        scanSettings = getScanSettings(ScanMode.LOW_LATENCY)

        // Register for broadcasts on BluetoothAdapter state change
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(adapterStateReceiver, filter)
    }
}