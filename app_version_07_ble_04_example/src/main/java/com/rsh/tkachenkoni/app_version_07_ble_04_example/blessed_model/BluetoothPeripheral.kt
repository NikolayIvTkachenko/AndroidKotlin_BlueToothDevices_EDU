package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model


import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Arrays
import java.util.Collections
import java.util.HashSet
import java.util.Locale
import java.util.Objects
import java.util.Queue
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.bluetooth.BluetoothDevice.BOND_BONDING
import android.bluetooth.BluetoothDevice.BOND_NONE
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ
import com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model.BluetoothBytesParser.Companion.bytes2String
import com.welie.blessed.BluetoothBytesParser.bytes2String


/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
class BluetoothPeripheral internal constructor(
    context: Context,
    device: BluetoothDevice,
    listener: InternalCallback,
    peripheralCallback: BluetoothPeripheralCallback,
    callbackHandler: Handler,
    transport: Transport
) {
    private val context: Context
    private val callbackHandler: Handler
    private var device: BluetoothDevice
    private val listener: InternalCallback
    protected var peripheralCallback: BluetoothPeripheralCallback
    private val commandQueue: Queue<Runnable> = ConcurrentLinkedQueue()

    @Nullable
    @Volatile
    private var bluetoothGatt: BluetoothGatt? = null
    private var cachedName = ""
    private var currentWriteBytes = ByteArray(0)
    private var currentCommand = IDLE
    private val notifyingCharacteristics: MutableSet<BluetoothGattCharacteristic> = HashSet()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Nullable
    private var timeoutRunnable: Runnable? = null

    @Nullable
    private var discoverServicesRunnable: Runnable? = null

    @Volatile
    private var commandQueueBusy = false
    private var isRetrying = false
    private var bondLost = false
    private var manuallyBonding = false

    @Volatile
    private var peripheralInitiatedBonding = false
    private var discoveryStarted = false

    @Volatile
    private var state = BluetoothProfile.STATE_DISCONNECTED
    private var nrTries = 0
    private var connectTimestamp: Long = 0
    private var currentMtu = DEFAULT_MTU
    private val transport: Transport

    /**
     * This abstract class is used to implement BluetoothGatt callbacks.
     */
    private val bluetoothGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            cancelConnectionTimer()
            val previousState = state
            state = newState
            val hciStatus = HciStatus.fromValue(status)
            if (hciStatus === HciStatus.SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> successfullyConnected()
                    BluetoothProfile.STATE_DISCONNECTED -> successfullyDisconnected(previousState)
                    BluetoothProfile.STATE_DISCONNECTING -> {
                        Logger.i(TAG, "peripheral '%s' is disconnecting", getAddress())
                        listener.disconnecting(this@BluetoothPeripheral)
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        Logger.i(TAG, "peripheral '%s' is connecting", getAddress())
                        listener.connecting(this@BluetoothPeripheral)
                    }
                    else -> Logger.e(TAG, "unknown state received")
                }
            } else {
                connectionStateChangeUnsuccessful(hciStatus, previousState, newState)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus !== GattStatus.SUCCESS) {
                Logger.e(
                    TAG,
                    "service discovery failed due to internal error '%s', disconnecting",
                    gattStatus
                )
                disconnect()
                return
            }
            val services = gatt.services
            Logger.i(TAG, "discovered %d services for '%s'", services.size, getName())

            // Issue 'connected' since we are now fully connect incl service discovery
            listener.connected(this@BluetoothPeripheral)
            callbackHandler.post { peripheralCallback.onServicesDiscovered(this@BluetoothPeripheral) }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val gattStatus = GattStatus.fromValue(status)
            val parentCharacteristic = descriptor.characteristic
            if (gattStatus !== GattStatus.SUCCESS) {
                Logger.e(
                    TAG,
                    "failed to write <%s> to descriptor of characteristic <%s> for device: '%s', status '%s' ",
                    bytes2String(currentWriteBytes),
                    parentCharacteristic.uuid,
                    getAddress(),
                    gattStatus
                )
                if (failureThatShouldTriggerBonding(gattStatus)) return
            }

            // Check if this was the Client Characteristic Configuration Descriptor
            if (descriptor.uuid == CCC_DESCRIPTOR_UUID) {
                if (gattStatus === GattStatus.SUCCESS) {
                    val value = nonnullOf(descriptor.value)
                    if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                        Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                    ) {
                        notifyingCharacteristics.add(parentCharacteristic)
                    } else if (Arrays.equals(
                            value,
                            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                        )
                    ) {
                        notifyingCharacteristics.remove(parentCharacteristic)
                    }
                }
                callbackHandler.post {
                    peripheralCallback.onNotificationStateUpdate(
                        this@BluetoothPeripheral,
                        parentCharacteristic,
                        gattStatus
                    )
                }
            } else {
                callbackHandler.post {
                    peripheralCallback.onDescriptorWrite(
                        this@BluetoothPeripheral,
                        currentWriteBytes,
                        descriptor,
                        gattStatus
                    )
                }
            }
            completedCommand()
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus !== GattStatus.SUCCESS) {
                Logger.e(
                    TAG,
                    "reading descriptor <%s> failed for device '%s, status '%s'",
                    descriptor.uuid,
                    getAddress(),
                    gattStatus
                )
                if (failureThatShouldTriggerBonding(gattStatus)) return
            }
            val value = nonnullOf(descriptor.value)
            callbackHandler.post {
                peripheralCallback.onDescriptorRead(
                    this@BluetoothPeripheral,
                    value,
                    descriptor,
                    gattStatus
                )
            }
            completedCommand()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = nonnullOf(characteristic.value)
            callbackHandler.post {
                peripheralCallback.onCharacteristicUpdate(
                    this@BluetoothPeripheral,
                    value,
                    characteristic,
                    GattStatus.SUCCESS
                )
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus !== GattStatus.SUCCESS) {
                Logger.e(
                    TAG,
                    "read failed for characteristic <%s>, status '%s'",
                    characteristic.uuid,
                    gattStatus
                )
                if (failureThatShouldTriggerBonding(gattStatus)) return
            }
            val value = nonnullOf(characteristic.value)
            callbackHandler.post {
                peripheralCallback.onCharacteristicUpdate(
                    this@BluetoothPeripheral,
                    value,
                    characteristic,
                    gattStatus
                )
            }
            completedCommand()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus !== GattStatus.SUCCESS) {
                Logger.e(
                    TAG,
                    "writing <%s> to characteristic <%s> failed, status '%s'",
                    bytes2String(currentWriteBytes),
                    characteristic.uuid,
                    gattStatus
                )
                if (failureThatShouldTriggerBonding(gattStatus)) return
            }
            val value = currentWriteBytes
            currentWriteBytes = ByteArray(0)
            callbackHandler.post {
                peripheralCallback.onCharacteristicWrite(
                    this@BluetoothPeripheral,
                    value,
                    characteristic,
                    gattStatus
                )
            }
            completedCommand()
        }

        private fun failureThatShouldTriggerBonding(gattStatus: GattStatus): Boolean {
            if (gattStatus === GattStatus.AUTHORIZATION_FAILED || gattStatus === GattStatus.INSUFFICIENT_AUTHENTICATION || gattStatus === GattStatus.INSUFFICIENT_ENCRYPTION) {
                // Characteristic/descriptor is encrypted and needs bonding, bonding should be in progress already
                // Operation must be retried after bonding is completed.
                // This only seems to happen on Android 5/6/7.
                // On newer versions Android will do retry internally
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    Logger.i(
                        TAG,
                        "operation will be retried after bonding, bonding should be in progress"
                    )
                    return true
                }
            }
            return false
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus !== GattStatus.SUCCESS) {
                Logger.e(TAG, "reading RSSI failed, status '%s'", gattStatus)
            }
            callbackHandler.post {
                peripheralCallback.onReadRemoteRssi(
                    this@BluetoothPeripheral,
                    rssi,
                    gattStatus
                )
            }
            completedCommand()
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus !== GattStatus.SUCCESS) {
                Logger.e(TAG, "change MTU failed, status '%s'", gattStatus)
            }
            currentMtu = mtu
            callbackHandler.post {
                peripheralCallback.onMtuChanged(
                    this@BluetoothPeripheral,
                    mtu,
                    gattStatus
                )
            }

            // Only complete the command if we initiated the operation. It can also be initiated by the remote peripheral...
            if (currentCommand == REQUEST_MTU_COMMAND) {
                currentCommand = IDLE
                completedCommand()
            }
        }

        override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus !== GattStatus.SUCCESS) {
                Logger.e(TAG, "read Phy failed, status '%s'", gattStatus)
            } else {
                Logger.i(
                    TAG,
                    "updated Phy: tx = %s, rx = %s",
                    PhyType.fromValue(txPhy),
                    PhyType.fromValue(rxPhy)
                )
            }
            callbackHandler.post {
                peripheralCallback.onPhyUpdate(
                    this@BluetoothPeripheral,
                    PhyType.fromValue(txPhy),
                    PhyType.fromValue(rxPhy),
                    gattStatus
                )
            }
            completedCommand()
        }

        override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus !== GattStatus.SUCCESS) {
                Logger.e(TAG, "update Phy failed, status '%s'", gattStatus)
            } else {
                Logger.i(
                    TAG,
                    "updated Phy: tx = %s, rx = %s",
                    PhyType.fromValue(txPhy),
                    PhyType.fromValue(rxPhy)
                )
            }
            callbackHandler.post {
                peripheralCallback.onPhyUpdate(
                    this@BluetoothPeripheral,
                    PhyType.fromValue(txPhy),
                    PhyType.fromValue(rxPhy),
                    gattStatus
                )
            }

            // Only complete the command if we initiated the operation. It can also be initiated by the remote peripheral...
            if (currentCommand == SET_PHY_TYPE_COMMAND) {
                currentCommand = IDLE
                completedCommand()
            }
        }

        /**
         * This callback is only called from Android 8 (Oreo) or higher. Not all phones seem to call this though...
         */
        fun onConnectionUpdated(
            gatt: BluetoothGatt,
            interval: Int,
            latency: Int,
            timeout: Int,
            status: Int
        ) {
            val gattStatus = GattStatus.fromValue(status)
            if (gattStatus === GattStatus.SUCCESS) {
                val msg = String.format(
                    Locale.ENGLISH,
                    "connection parameters: interval=%.1fms latency=%d timeout=%ds",
                    interval * 1.25f,
                    latency,
                    timeout / 100
                )
                Logger.d(TAG, msg)
            } else {
                Logger.e(TAG, "connection parameters update failed with status '%s'", gattStatus)
            }
            callbackHandler.post {
                peripheralCallback.onConnectionUpdated(
                    this@BluetoothPeripheral,
                    interval,
                    latency,
                    timeout,
                    gattStatus
                )
            }
        }
    }

    private fun successfullyConnected() {
        val bondstate = getBondState()
        val timePassed = SystemClock.elapsedRealtime() - connectTimestamp
        Logger.i(TAG, "connected to '%s' (%s) in %.1fs", getName(), bondstate, timePassed / 1000.0f)
        if (bondstate === BondState.NONE || bondstate === BondState.BONDED) {
            delayedDiscoverServices(getServiceDiscoveryDelay(bondstate))
        } else if (bondstate === BondState.BONDING) {
            // Apparently the bonding process has already started, so let it complete. We'll do discoverServices once bonding finished
            Logger.i(TAG, "waiting for bonding to complete")
        }
    }

    private fun delayedDiscoverServices(delay: Long) {
        discoverServicesRunnable = Runnable {
            Logger.d(TAG, "discovering services of '%s' with delay of %d ms", getName(), delay)
            if (bluetoothGatt != null && bluetoothGatt!!.discoverServices()) {
                discoveryStarted = true
            } else {
                Logger.e(TAG, "discoverServices failed to start")
            }
            discoverServicesRunnable = null
        }
        mainHandler.postDelayed(discoverServicesRunnable, delay)
    }

    private fun getServiceDiscoveryDelay(bondstate: BondState): Long {
        var delayWhenBonded: Long = 0
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            // It seems delays when bonded are only needed in versions Nougat or lower
            // This issue was observed on a Nexus 5 (M) and Sony Xperia L1 (N) when connecting to a A&D UA-651BLE
            // The delay is needed when devices have the Service Changed Characteristic.
            // If they don't have it the delay isn't needed but we do it anyway to keep code simple
            delayWhenBonded = 1000L
        }
        return if (bondstate === BondState.BONDED) delayWhenBonded else 0
    }

    private fun successfullyDisconnected(previousState: Int) {
        if (previousState == BluetoothProfile.STATE_CONNECTED || previousState == BluetoothProfile.STATE_DISCONNECTING) {
            Logger.i(TAG, "disconnected '%s' on request", getName())
        } else if (previousState == BluetoothProfile.STATE_CONNECTING) {
            Logger.i(TAG, "cancelling connect attempt")
        }
        if (bondLost) {
            completeDisconnect(false, HciStatus.SUCCESS)

            // Consider the loss of the bond a connection failure so that a connection retry will take place
            callbackHandler.postDelayed(
                {
                    listener.connectFailed(
                        this@BluetoothPeripheral,
                        HciStatus.SUCCESS
                    )
                },
                DELAY_AFTER_BOND_LOST
            ) // Give the stack some time to register the bond loss internally. This is needed on most phones...
        } else {
            completeDisconnect(true, HciStatus.SUCCESS)
        }
    }

    private fun connectionStateChangeUnsuccessful(
        status: HciStatus,
        previousState: Int,
        newState: Int
    ) {
        cancelPendingServiceDiscovery()
        val servicesDiscovered = !getServices().isEmpty()

        // See if the initial connection failed
        if (previousState == BluetoothProfile.STATE_CONNECTING) {
            val timePassed = SystemClock.elapsedRealtime() - connectTimestamp
            val isTimeout = timePassed > getTimoutThreshold()
            val adjustedStatus =
                if (status === HciStatus.ERROR && isTimeout) HciStatus.CONNECTION_FAILED_ESTABLISHMENT else status
            Logger.i(TAG, "connection failed with status '%s'", adjustedStatus)
            completeDisconnect(false, adjustedStatus)
            listener.connectFailed(this@BluetoothPeripheral, adjustedStatus)
        } else if (previousState == BluetoothProfile.STATE_CONNECTED && newState == BluetoothProfile.STATE_DISCONNECTED && !servicesDiscovered) {
            // We got a disconnection before the services were even discovered
            Logger.i(
                TAG,
                "peripheral '%s' disconnected with status '%s' (%d) before completing service discovery",
                getName(),
                status,
                status.value
            )
            completeDisconnect(false, status)
            listener.connectFailed(this@BluetoothPeripheral, status)
        } else {
            // See if we got connection drop
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Logger.i(
                    TAG,
                    "peripheral '%s' disconnected with status '%s' (%d)",
                    getName(),
                    status,
                    status.value
                )
            } else {
                Logger.i(
                    TAG,
                    "unexpected connection state change for '%s' status '%s' (%d)",
                    getName(),
                    status,
                    status.value
                )
            }
            completeDisconnect(true, status)
        }
    }

    private fun cancelPendingServiceDiscovery() {
        if (discoverServicesRunnable != null) {
            mainHandler.removeCallbacks(discoverServicesRunnable!!)
            discoverServicesRunnable = null
        }
    }

    private val bondStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val receivedDevice =
                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    ?: return

            // Ignore updates for other devices
            if (!receivedDevice.address.equals(getAddress(), ignoreCase = true)) return
            if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val bondState =
                    intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                val previousBondState = intent.getIntExtra(
                    BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                    BluetoothDevice.ERROR
                )
                handleBondStateChange(bondState, previousBondState)
            }
        }
    }

    private fun handleBondStateChange(bondState: Int, previousBondState: Int) {
        when (bondState) {
            BOND_BONDING -> {
                Logger.d(TAG, "starting bonding with '%s' (%s)", getName(), getAddress())
                callbackHandler.post { peripheralCallback.onBondingStarted(this@BluetoothPeripheral) }
            }
            BOND_BONDED -> {
                Logger.d(TAG, "bonded with '%s' (%s)", getName(), getAddress())
                callbackHandler.post { peripheralCallback.onBondingSucceeded(this@BluetoothPeripheral) }

                // If bonding was started at connection time, we may still have to discover the services
                // Also make sure we are not starting a discovery while another one is already in progress
                if (getServices().isEmpty() && !discoveryStarted) {
                    delayedDiscoverServices(0)
                }

                // If bonding was triggered by a read/write, we must retry it
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    if (commandQueueBusy && !manuallyBonding) {
                        mainHandler.postDelayed({
                            Logger.d(TAG, "retrying command after bonding")
                            retryCommand()
                        }, 50)
                    }
                }

                // If we are doing a manual bond, complete the command
                if (manuallyBonding) {
                    manuallyBonding = false
                    completedCommand()
                }

                // If the peripheral initated the bonding, continue the queue
                if (peripheralInitiatedBonding) {
                    peripheralInitiatedBonding = false
                    nextCommand()
                }
            }
            BOND_NONE -> {
                if (previousBondState == BOND_BONDING) {
                    Logger.e(TAG, "bonding failed for '%s', disconnecting device", getName())
                    callbackHandler.post { peripheralCallback.onBondingFailed(this@BluetoothPeripheral) }
                } else {
                    Logger.e(TAG, "bond lost for '%s'", getName())
                    bondLost = true

                    // Cancel the discoverServiceRunnable if it is still pending
                    cancelPendingServiceDiscovery()
                    callbackHandler.post { peripheralCallback.onBondLost(this@BluetoothPeripheral) }
                }
                disconnect()
            }
        }
    }

    private val pairingRequestBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val receivedDevice =
                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    ?: return

            // Skip other devices
            if (!receivedDevice.address.equals(getAddress(), ignoreCase = true)) return
            val variant =
                intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
            Logger.d(
                TAG,
                "pairing request received: " + pairingVariantToString(variant) + " (" + variant + ")"
            )
            if (variant == PAIRING_VARIANT_PIN) {
                val pin = listener.getPincode(this@BluetoothPeripheral)
                if (pin != null) {
                    Logger.d(TAG, "setting PIN code for this peripheral using '%s'", pin)
                    receivedDevice.setPin(pin.toByteArray())
                    abortBroadcast()
                }
            }
        }
    }

    @JvmName("setPeripheralCallback1")
    fun setPeripheralCallback(peripheralCallback: BluetoothPeripheralCallback) {
        this.peripheralCallback =
            Objects.requireNonNull(peripheralCallback, NO_VALID_PERIPHERAL_CALLBACK_PROVIDED)
    }

    fun setDevice(bluetoothDevice: BluetoothDevice) {
        device = Objects.requireNonNull(bluetoothDevice, NO_VALID_DEVICE_PROVIDED)
    }

    /**
     * Connect directly with the bluetooth device. This call will timeout in max 30 seconds (5 seconds on Samsung phones)
     */
    fun connect() {
        // Make sure we are disconnected before we start making a connection
        if (state == BluetoothProfile.STATE_DISCONNECTED) {
            mainHandler.postDelayed({ // Connect to device with autoConnect = false
                Logger.i(
                    TAG,
                    "connect to '%s' (%s) using transport %s",
                    getName(),
                    getAddress(),
                    transport.name()
                )
                registerBondingBroadcastReceivers()
                discoveryStarted = false
                bluetoothGatt = connectGattHelper(device, false, bluetoothGattCallback)
                bluetoothGattCallback.onConnectionStateChange(
                    bluetoothGatt,
                    HciStatus.SUCCESS.value,
                    BluetoothProfile.STATE_CONNECTING
                )
                connectTimestamp = SystemClock.elapsedRealtime()
                startConnectionTimer(this@BluetoothPeripheral)
            }, DIRECT_CONNECTION_DELAY_IN_MS.toLong())
        } else {
            Logger.e(TAG, "peripheral '%s' not yet disconnected, will not connect", getName())
        }
    }

    /**
     * Try to connect to a device whenever it is found by the OS. This call never times out.
     * Connecting to a device will take longer than when using connect()
     */
    fun autoConnect() {
        // Note that this will only work for devices that are known! After turning BT on/off Android doesn't know the device anymore!
        // https://stackoverflow.com/questions/43476369/android-save-ble-device-to-reconnect-after-app-close
        if (state == BluetoothProfile.STATE_DISCONNECTED) {
            mainHandler.post { // Connect to device with autoConnect = true
                Logger.i(
                    TAG,
                    "autoConnect to '%s' (%s) using transport %s",
                    getName(),
                    getAddress(),
                    transport.name()
                )
                registerBondingBroadcastReceivers()
                discoveryStarted = false
                bluetoothGatt = connectGattHelper(device, true, bluetoothGattCallback)
                bluetoothGattCallback.onConnectionStateChange(
                    bluetoothGatt,
                    HciStatus.SUCCESS.value,
                    BluetoothProfile.STATE_CONNECTING
                )
                connectTimestamp = SystemClock.elapsedRealtime()
            }
        } else {
            Logger.e(TAG, "peripheral '%s' not yet disconnected, will not connect", getName())
        }
    }

    private fun registerBondingBroadcastReceivers() {
        context.registerReceiver(
            bondStateReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )
        context.registerReceiver(
            pairingRequestBroadcastReceiver,
            IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST)
        )
    }

    /**
     * Create a bond with the peripheral.
     *
     *
     * If a (auto)connect has been issued, the bonding command will be enqueued and you will
     * receive updates via the [BluetoothPeripheralCallback]. Otherwise the bonding will
     * be done immediately and no updates via the callback will happen.
     *
     * @return true if bonding was started/enqueued, false if not
     */
    fun createBond(): Boolean {
        // Check if we have a Gatt object
        if (bluetoothGatt == null) {
            // No gatt object so no connection issued, do create bond immediately
            return device.createBond()
        }

        // Enqueue the bond command because a connection has been issued or we are already connected
        val result = commandQueue.add(Runnable {
            manuallyBonding = true
            if (!device.createBond()) {
                Logger.e(TAG, "bonding failed for %s", getAddress())
                completedCommand()
            } else {
                Logger.d(TAG, "manually bonding %s", getAddress())
                nrTries++
            }
        })
        if (result) {
            nextCommand()
        } else {
            Logger.e(TAG, "could not enqueue bonding command")
        }
        return result
    }

    /**
     * Cancel an active or pending connection.
     *
     *
     * This operation is asynchronous and you will receive a callback on onDisconnectedPeripheral.
     */
    fun cancelConnection() {
        // Check if we have a Gatt object
        if (bluetoothGatt == null) {
            Logger.w(TAG, "cannot cancel connection because no connection attempt is made yet")
            return
        }

        // Check if we are not already disconnected or disconnecting
        if (state == BluetoothProfile.STATE_DISCONNECTED || state == BluetoothProfile.STATE_DISCONNECTING) {
            return
        }

        // Cancel the connection timer
        cancelConnectionTimer()

        // Check if we are in the process of connecting
        if (state == BluetoothProfile.STATE_CONNECTING) {
            // Cancel the connection by calling disconnect
            disconnect()

            // Since we will not get a callback on onConnectionStateChange for this, we issue the disconnect ourselves
            mainHandler.postDelayed({
                bluetoothGattCallback.onConnectionStateChange(
                    bluetoothGatt,
                    HciStatus.SUCCESS.value,
                    BluetoothProfile.STATE_DISCONNECTED
                )
            }, 50)
        } else {
            // Cancel active connection and onConnectionStateChange will be called by Android
            disconnect()
        }
    }

    /**
     * Disconnect the bluetooth peripheral.
     *
     *
     * When the disconnection has been completed [BluetoothCentralManagerCallback.onDisconnectedPeripheral] will be called.
     */
    private fun disconnect() {
        if (state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_CONNECTING) {
            bluetoothGattCallback.onConnectionStateChange(
                bluetoothGatt,
                HciStatus.SUCCESS.value,
                BluetoothProfile.STATE_DISCONNECTING
            )
            mainHandler.post {
                if (state == BluetoothProfile.STATE_DISCONNECTING && bluetoothGatt != null) {
                    bluetoothGatt!!.disconnect()
                    Logger.i(
                        TAG,
                        "force disconnect '%s' (%s)",
                        getName(),
                        getAddress()
                    )
                }
            }
        } else {
            listener.disconnected(this@BluetoothPeripheral, HciStatus.SUCCESS)
        }
    }

    fun disconnectWhenBluetoothOff() {
        bluetoothGatt = null
        completeDisconnect(true, HciStatus.SUCCESS)
    }

    /**
     * Complete the disconnect after getting connectionstate == disconnected
     */
    private fun completeDisconnect(notify: Boolean, status: HciStatus) {
        if (bluetoothGatt != null) {
            bluetoothGatt!!.close()
            bluetoothGatt = null
        }
        commandQueue.clear()
        commandQueueBusy = false
        notifyingCharacteristics.clear()
        try {
            context.unregisterReceiver(bondStateReceiver)
            context.unregisterReceiver(pairingRequestBroadcastReceiver)
        } catch (e: IllegalArgumentException) {
            // In case bluetooth is off, unregisering broadcast receivers may fail
        }
        bondLost = false
        if (notify) {
            listener.disconnected(this@BluetoothPeripheral, status)
        }
    }

    /**
     * Get the mac address of the bluetooth peripheral.
     *
     * @return Address of the bluetooth peripheral
     */
    fun getAddress(): String {
        return device.address
    }

    /**
     * Get the type of the peripheral.
     *
     * @return the PeripheralType
     */
    fun getType(): PeripheralType {
        return PeripheralType.fromValue(device.type)
    }

    /**
     * Get the name of the bluetooth peripheral.
     *
     * @return name of the bluetooth peripheral
     */
    fun getName(): String {
        val name = device.name
        if (name != null) {
            // Cache the name so that we even know it when bluetooth is switched off
            cachedName = name
            return name
        }
        return cachedName
    }

    /**
     * Get the bond state of the bluetooth peripheral.
     *
     * @return the bond state
     */
    fun getBondState(): BondState {
        return BondState.fromValue(device.bondState)
    }

    /**
     * Get the services supported by the connected bluetooth peripheral.
     * Only services that are also supported by [BluetoothCentralManager] are included.
     *
     * @return Supported services.
     */
    fun getServices(): List<BluetoothGattService> {
        return if (bluetoothGatt != null) {
            bluetoothGatt!!.services
        } else emptyList()
    }

    /**
     * Get the BluetoothGattService object for a service UUID.
     *
     * @param serviceUUID the UUID of the service
     * @return the BluetoothGattService object for the service UUID or null if the peripheral does not have a service with the specified UUID
     */
    @Nullable
    fun getService(serviceUUID: UUID): BluetoothGattService? {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED)
        return if (bluetoothGatt != null) {
            bluetoothGatt!!.getService(serviceUUID)
        } else {
            null
        }
    }

    /**
     * Get the BluetoothGattCharacteristic object for a characteristic UUID.
     *
     * @param serviceUUID        the service UUID the characteristic is part of
     * @param characteristicUUID the UUID of the chararacteristic
     * @return the BluetoothGattCharacteristic object for the characteristic UUID or null if the peripheral does not have a characteristic with the specified UUID
     */
    @Nullable
    fun getCharacteristic(
        serviceUUID: UUID,
        characteristicUUID: UUID
    ): BluetoothGattCharacteristic? {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED)
        Objects.requireNonNull(characteristicUUID, NO_VALID_CHARACTERISTIC_UUID_PROVIDED)
        val service = getService(serviceUUID)
        return service?.getCharacteristic(characteristicUUID)
    }

    /**
     * Returns the connection state of the peripheral.
     *
     * @return the connection state.
     */
    fun getState(): ConnectionState {
        return ConnectionState.fromValue(state)
    }

    /**
     * Returns the currently set MTU
     *
     * @return the MTU
     */
    fun getCurrentMtu(): Int {
        return currentMtu
    }

    /**
     * Get maximum length of byte array that can be written depending on WriteType
     *
     *
     *
     * This value is derived from the current negotiated MTU or the maximum characteristic length (512)
     */
    fun getMaximumWriteValueLength(writeType: WriteType): Int {
        Objects.requireNonNull<Any>(writeType, "writetype is null")
        return when (writeType) {
            WITH_RESPONSE -> 512
            SIGNED -> currentMtu - 15
            else -> currentMtu - 3
        }
    }

    /**
     * Returns the transport used during connection phase.
     * @return the Transport.
     */
    fun getTransport(): Transport {
        return transport
    }

    /**
     * Boolean to indicate if the specified characteristic is currently notifying or indicating.
     *
     * @param characteristic the characteristic to check
     * @return true if the characteristic is notifying or indicating, false if it is not
     */
    fun isNotifying(characteristic: BluetoothGattCharacteristic): Boolean {
        Objects.requireNonNull(characteristic, NO_VALID_CHARACTERISTIC_PROVIDED)
        return notifyingCharacteristics.contains(characteristic)
    }

    /**
     * Get all notifying/indicating characteristics
     *
     * @return Set of characteristics or empty set
     */
    fun getNotifyingCharacteristics(): Set<BluetoothGattCharacteristic> {
        return Collections.unmodifiableSet(notifyingCharacteristics)
    }

    private fun isConnected(): Boolean {
        return bluetoothGatt != null && state == BluetoothProfile.STATE_CONNECTED
    }

    private fun notConnected(): Boolean {
        return !isConnected()
    }

    /**
     * Check if the peripheral is uncached by the Android BLE stack
     *
     * @return true if unchached, otherwise false
     */
    fun isUncached(): Boolean {
        return getType() === PeripheralType.UNKNOWN
    }

    /**
     * Read the value of a characteristic.
     *
     * Convenience function to read a characteristic without first having to find it.
     *
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @return true if the operation was enqueued, false if the characteristic was not found
     * @throws IllegalArgumentException if the characteristic does not support reading
     */
    fun readCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): Boolean {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED)
        Objects.requireNonNull(characteristicUUID, NO_VALID_CHARACTERISTIC_UUID_PROVIDED)
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
        return characteristic?.let { readCharacteristic(it) } ?: false
    }

    /**
     * Read the value of a characteristic.
     *
     *
     * [BluetoothPeripheralCallback.onCharacteristicUpdate]   will be triggered as a result of this call.
     *
     * @param characteristic Specifies the characteristic to read.
     * @return true if the operation was enqueued, otherwise false
     * @throws IllegalArgumentException if the characteristic does not support reading
     */
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        Objects.requireNonNull(characteristic, NO_VALID_CHARACTERISTIC_PROVIDED)
        if (notConnected()) {
            Logger.e(TAG, PERIPHERAL_NOT_CONNECTED)
            return false
        }
        if (doesNotSupportReading(characteristic)) {
            val message = String.format(
                "characteristic <%s> does not have read property",
                characteristic.uuid
            )
            throw IllegalArgumentException(message)
        }
        val result = commandQueue.add(Runnable {
            if (isConnected()) {
                if (bluetoothGatt!!.readCharacteristic(characteristic)) {
                    Logger.d(TAG, "reading characteristic <%s>", characteristic.uuid)
                    nrTries++
                } else {
                    Logger.e(
                        TAG,
                        "readCharacteristic failed for characteristic: %s",
                        characteristic.uuid
                    )
                    completedCommand()
                }
            } else {
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Logger.e(TAG, "could not enqueue read characteristic command")
        }
        return result
    }

    private fun doesNotSupportReading(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.properties and PROPERTY_READ == 0
    }

    /**
     * Write a value to a characteristic using the specified write type.
     *
     * Convenience function to write a characteristic without first having to find it.
     * All parameters must have a valid value in order for the operation to be enqueued.
     *
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @param value              the byte array to write
     * @param writeType          the write type to use when writing.
     * @return true if the operation was enqueued, otherwise false
     * @throws IllegalArgumentException if the characteristic does not support writing with the specified writeType or the byte array is empty or too long
     */
    fun writeCharacteristic(
        serviceUUID: UUID,
        characteristicUUID: UUID,
        value: ByteArray,
        writeType: WriteType
    ): Boolean {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED)
        Objects.requireNonNull(characteristicUUID, NO_VALID_CHARACTERISTIC_UUID_PROVIDED)
        Objects.requireNonNull(value, NO_VALID_VALUE_PROVIDED)
        Objects.requireNonNull<Any>(writeType, NO_VALID_WRITE_TYPE_PROVIDED)
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
        return characteristic?.let { writeCharacteristic(it, value, writeType) } ?: false
    }

    /**
     * Write a value to a characteristic using the specified write type.
     *
     *
     * All parameters must have a valid value in order for the operation to be enqueued.
     * The length of the byte array to write must be between 1 and getMaximumWriteValueLength(writeType).
     *
     *
     * [BluetoothPeripheralCallback.onCharacteristicWrite] will be triggered as a result of this call.
     *
     * @param characteristic the characteristic to write to
     * @param value          the byte array to write
     * @param writeType      the write type to use when writing.
     * @return true if a write operation was succesfully enqueued, otherwise false
     * @throws IllegalArgumentException if the characteristic does not support writing with the specified writeType or the byte array is empty or too long
     */
    fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: WriteType
    ): Boolean {
        Objects.requireNonNull(characteristic, NO_VALID_CHARACTERISTIC_PROVIDED)
        Objects.requireNonNull(value, NO_VALID_VALUE_PROVIDED)
        Objects.requireNonNull<Any>(writeType, NO_VALID_WRITE_TYPE_PROVIDED)
        if (notConnected()) {
            Logger.e(TAG, PERIPHERAL_NOT_CONNECTED)
            return false
        }
        require(value.size != 0) { VALUE_BYTE_ARRAY_IS_EMPTY }
        require(value.size <= getMaximumWriteValueLength(writeType)) { VALUE_BYTE_ARRAY_IS_TOO_LONG }
        if (doesNotSupportWriteType(characteristic, writeType)) {
            val message = java.lang.String.format(
                "characteristic <%s> does not support writeType '%s'",
                characteristic.uuid,
                writeType
            )
            throw IllegalArgumentException(message)
        }

        // Copy the value to avoid race conditions
        val bytesToWrite = copyOf(value)
        val result = commandQueue.add(Runnable {
            if (isConnected()) {
                currentWriteBytes = bytesToWrite
                characteristic.writeType = writeType.writeType
                if (willCauseLongWrite(bytesToWrite, writeType)) {
                    // Android will turn this into a Long Write because it is larger than the MTU - 3.
                    // When doing a Long Write the byte array will be automatically split in chunks of size MTU - 3.
                    // However, the peripheral's firmware must also support it, so it is not guaranteed to work.
                    // Long writes are also very inefficient because of the confirmation of each write operation.
                    // So it is better to increase MTU if possible. Hence a warning if this write becomes a long write...
                    // See https://stackoverflow.com/questions/48216517/rxandroidble-write-only-sends-the-first-20b
                    Logger.w(
                        TAG,
                        "value byte array is longer than allowed by MTU, write will fail if peripheral does not support long writes"
                    )
                }
                characteristic.value = bytesToWrite
                if (bluetoothGatt!!.writeCharacteristic(characteristic)) {
                    Logger.d(
                        TAG,
                        "writing <%s> to characteristic <%s>",
                        bytes2String(bytesToWrite),
                        characteristic.uuid
                    )
                    nrTries++
                } else {
                    Logger.e(
                        TAG,
                        "writeCharacteristic failed for characteristic: %s",
                        characteristic.uuid
                    )
                    completedCommand()
                }
            } else {
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Logger.e(TAG, "could not enqueue write characteristic command")
        }
        return result
    }

    private fun willCauseLongWrite(value: ByteArray, writeType: WriteType): Boolean {
        return value.size > currentMtu - 3 && writeType === WriteType.WITH_RESPONSE
    }

    private fun doesNotSupportWriteType(
        characteristic: BluetoothGattCharacteristic,
        writeType: WriteType
    ): Boolean {
        return characteristic.properties and writeType.property === 0
    }

    /**
     * Read the value of a descriptor.
     *
     * @param descriptor the descriptor to read
     * @return true if a write operation was succesfully enqueued, otherwise false
     */
    fun readDescriptor(descriptor: BluetoothGattDescriptor): Boolean {
        Objects.requireNonNull(descriptor, NO_VALID_DESCRIPTOR_PROVIDED)
        if (notConnected()) {
            Logger.e(TAG, PERIPHERAL_NOT_CONNECTED)
            return false
        }
        val result = commandQueue.add(Runnable {
            if (isConnected()) {
                if (bluetoothGatt!!.readDescriptor(descriptor)) {
                    Logger.d(TAG, "reading descriptor <%s>", descriptor.uuid)
                    nrTries++
                } else {
                    Logger.e(TAG, "readDescriptor failed for characteristic: %s", descriptor.uuid)
                    completedCommand()
                }
            } else {
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Logger.e(TAG, "could not enqueue read descriptor command")
        }
        return result
    }

    /**
     * Write a value to a descriptor.
     *
     *
     * For turning on/off notifications use [BluetoothPeripheral.setNotify] instead.
     *
     * @param descriptor the descriptor to write to
     * @param value      the value to write
     * @return true if a write operation was succesfully enqueued, otherwise false
     */
    fun writeDescriptor(descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean {
        Objects.requireNonNull(descriptor, NO_VALID_DESCRIPTOR_PROVIDED)
        Objects.requireNonNull(value, NO_VALID_VALUE_PROVIDED)
        if (notConnected()) {
            Logger.e(TAG, PERIPHERAL_NOT_CONNECTED)
            return false
        }
        require(value.size != 0) { VALUE_BYTE_ARRAY_IS_EMPTY }
        require(value.size <= getMaximumWriteValueLength(WriteType.WITH_RESPONSE)) { VALUE_BYTE_ARRAY_IS_TOO_LONG }

        // Copy the value to avoid race conditions
        val bytesToWrite = copyOf(value)
        val result = commandQueue.add(Runnable {
            if (isConnected()) {
                currentWriteBytes = bytesToWrite
                descriptor.value = bytesToWrite
                if (bluetoothGatt!!.writeDescriptor(descriptor)) {
                    Logger.d(
                        TAG,
                        "writing <%s> to descriptor <%s>",
                        bytes2String(bytesToWrite),
                        descriptor.uuid
                    )
                    nrTries++
                } else {
                    Logger.e(TAG, "writeDescriptor failed for descriptor: %s", descriptor.uuid)
                    completedCommand()
                }
            } else {
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Logger.e(TAG, "could not enqueue write descriptor command")
        }
        return result
    }

    /**
     * Set the notification state of a characteristic to 'on' or 'off'. The characteristic must support notifications or indications.
     *
     * @param serviceUUID        the service UUID the characteristic belongs to
     * @param characteristicUUID the characteristic's UUID
     * @param enable             true for setting notification on, false for turning it off
     * @return true if the operation was enqueued, otherwise false
     * @throws IllegalArgumentException if the CCC descriptor was not found or the characteristic does not support notifications or indications
     */
    fun setNotify(serviceUUID: UUID, characteristicUUID: UUID, enable: Boolean): Boolean {
        Objects.requireNonNull(serviceUUID, NO_VALID_SERVICE_UUID_PROVIDED)
        Objects.requireNonNull(characteristicUUID, NO_VALID_CHARACTERISTIC_UUID_PROVIDED)
        val characteristic = getCharacteristic(serviceUUID, characteristicUUID)
        return characteristic?.let { setNotify(it, enable) } ?: false
    }

    /**
     * Set the notification state of a characteristic to 'on' or 'off'. The characteristic must support notifications or indications.
     *
     *
     * [BluetoothPeripheralCallback.onNotificationStateUpdate] will be triggered as a result of this call.
     *
     * @param characteristic the characteristic to turn notification on/off for
     * @param enable         true for setting notification on, false for turning it off
     * @return true if the operation was enqueued, otherwise false
     * @throws IllegalArgumentException if the CCC descriptor was not found or the characteristic does not support notifications or indications
     */
    fun setNotify(characteristic: BluetoothGattCharacteristic, enable: Boolean): Boolean {
        Objects.requireNonNull(characteristic, NO_VALID_CHARACTERISTIC_PROVIDED)
        if (notConnected()) {
            Logger.e(TAG, PERIPHERAL_NOT_CONNECTED)
            return false
        }

        // Get the Client Characteristic Configuration Descriptor for the characteristic
        val descriptor = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID)
        if (descriptor == null) {
            val message = String.format(
                "could not get CCC descriptor for characteristic %s",
                characteristic.uuid
            )
            throw IllegalArgumentException(message)
        }

        // Check if characteristic has NOTIFY or INDICATE properties and set the correct byte value to be written
        val value: ByteArray
        val properties = characteristic.properties
        value = if (properties and PROPERTY_NOTIFY > 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else if (properties and PROPERTY_INDICATE > 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            val message = String.format(
                "characteristic %s does not have notify or indicate property",
                characteristic.uuid
            )
            throw IllegalArgumentException(message)
        }
        val finalValue = if (enable) value else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        val result = commandQueue.add(Runnable {
            if (notConnected()) {
                completedCommand()
                return@Runnable
            }

            // First try to set notification for Gatt object
            if (!bluetoothGatt!!.setCharacteristicNotification(characteristic, enable)) {
                Logger.e(
                    TAG,
                    "setCharacteristicNotification failed for characteristic: %s",
                    characteristic.uuid
                )
                completedCommand()
                return@Runnable
            }

            // Then write to CCC descriptor
            adjustWriteTypeIfNeeded(characteristic)
            currentWriteBytes = finalValue
            descriptor.value = finalValue
            if (bluetoothGatt!!.writeDescriptor(descriptor)) {
                nrTries++
            } else {
                Logger.e(TAG, "writeDescriptor failed for descriptor: %s", descriptor.uuid)
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Logger.e(TAG, "could not enqueue setNotify command")
        }
        return result
    }

    private fun adjustWriteTypeIfNeeded(characteristic: BluetoothGattCharacteristic) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // Up to Android 6 there is a bug where Android takes the writeType of the parent characteristic instead of always WRITE_TYPE_DEFAULT
            // See: https://android.googlesource.com/platform/frameworks/base/+/942aebc95924ab1e7ea1e92aaf4e7fc45f695a6c%5E%21/#F0
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
    }

    /**
     * Read the RSSI for a connected remote peripheral.
     *
     *
     * [BluetoothPeripheralCallback.onReadRemoteRssi] will be triggered as a result of this call.
     *
     * @return true if the operation was enqueued, false otherwise
     */
    fun readRemoteRssi(): Boolean {
        if (notConnected()) {
            Logger.e(TAG, PERIPHERAL_NOT_CONNECTED)
            return false
        }
        val result = commandQueue.add(Runnable {
            if (isConnected()) {
                if (!bluetoothGatt!!.readRemoteRssi()) {
                    Logger.e(TAG, "readRemoteRssi failed")
                    completedCommand()
                }
            } else {
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Logger.e(TAG, "could not enqueue readRemoteRssi command")
        }
        return result
    }

    /**
     * Request an MTU size used for a given connection.
     *
     *
     * When performing a write request operation (write without response),
     * the data sent is truncated to the MTU size. This function may be used
     * to request a larger MTU size to be able to send more data at once.
     *
     *
     * Note that requesting an MTU should only take place once per connection, according to the Bluetooth standard.
     *
     * [BluetoothPeripheralCallback.onMtuChanged] will be triggered as a result of this call.
     *
     * @param mtu the desired MTU size
     * @return true if the operation was enqueued, false otherwise
     */
    fun requestMtu(mtu: Int): Boolean {
        require(!(mtu < DEFAULT_MTU || mtu > MAX_MTU)) { "mtu must be between 23 and 517" }
        if (notConnected()) {
            Logger.e(TAG, PERIPHERAL_NOT_CONNECTED)
            return false
        }
        val result = commandQueue.add(Runnable {
            if (isConnected()) {
                if (bluetoothGatt!!.requestMtu(mtu)) {
                    currentCommand = REQUEST_MTU_COMMAND
                    Logger.i(TAG, "requesting MTU of %d", mtu)
                } else {
                    Logger.e(TAG, "requestMtu failed")
                    completedCommand()
                }
            } else {
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Logger.e(TAG, "could not enqueue requestMtu command")
        }
        return result
    }

    /**
     * Request a different connection priority.
     *
     * @param priority the requested connection priority
     * @return true if request was enqueued, false if not
     */
    fun requestConnectionPriority(priority: ConnectionPriority): Boolean {
        Objects.requireNonNull(priority, NO_VALID_PRIORITY_PROVIDED)
        if (notConnected()) {
            Logger.e(TAG, PERIPHERAL_NOT_CONNECTED)
            return false
        }
        val result = commandQueue.add(Runnable {
            if (isConnected()) {
                if (bluetoothGatt!!.requestConnectionPriority(priority.value)) {
                    Logger.d(TAG, "requesting connection priority %s", priority)
                } else {
                    Logger.e(TAG, "could not request connection priority")
                }
            }

            // Complete command as there is no reliable callback for this, but allow some time
            callbackHandler.postDelayed(
                { completedCommand() },
                AVG_REQUEST_CONNECTION_PRIORITY_DURATION
            )
        })
        if (result) {
            nextCommand()
        } else {
            Logger.e(TAG, "could not enqueue request connection priority command")
        }
        return result
    }

    /**
     * Set the preferred connection PHY for this app. Please note that this is just a
     * recommendation, whether the PHY change will happen depends on other applications preferences,
     * local and remote controller capabilities. Controller can override these settings.
     *
     *
     * [BluetoothPeripheralCallback.onPhyUpdate] will be triggered as a result of this call, even
     * if no PHY change happens. It is also triggered when remote device updates the PHY.
     *
     * @param txPhy      the desired TX PHY
     * @param rxPhy      the desired RX PHY
     * @param phyOptions the desired optional sub-type for PHY_LE_CODED
     * @return true if request was enqueued, false if not
     */
    fun setPreferredPhy(txPhy: PhyType, rxPhy: PhyType, phyOptions: PhyOptions): Boolean {
        Objects.requireNonNull<Any>(txPhy)
        Objects.requireNonNull<Any>(rxPhy)
        Objects.requireNonNull<Any>(phyOptions)
        if (notConnected()) {
            Logger.e(TAG, PERIPHERAL_NOT_CONNECTED)
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Logger.e(TAG, "setPreferredPhy requires Android 8.0 or newer")
            return false
        }
        val result = commandQueue.add(Runnable {
            if (isConnected()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    currentCommand = SET_PHY_TYPE_COMMAND
                    Logger.i(
                        TAG,
                        "setting preferred Phy: tx = %s, rx = %s, options = %s",
                        txPhy,
                        rxPhy,
                        phyOptions
                    )
                    bluetoothGatt!!.setPreferredPhy(txPhy.mask, rxPhy.mask, phyOptions.value)
                }
            } else {
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Logger.e(TAG, "could not enqueue setPreferredPhy command")
        }
        return result
    }

    /**
     * Read the current transmitter PHY and receiver PHY of the connection. The values are returned
     * in [BluetoothPeripheralCallback.onPhyUpdate]
     */
    fun readPhy(): Boolean {
        if (notConnected()) {
            Logger.e(TAG, PERIPHERAL_NOT_CONNECTED)
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Logger.e(TAG, "setPreferredPhy requires Android 8.0 or newer")
            return false
        }
        val result = commandQueue.add(Runnable {
            if (isConnected()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    bluetoothGatt!!.readPhy()
                    Logger.d(TAG, "reading Phy")
                }
            } else {
                completedCommand()
            }
        })
        if (result) {
            nextCommand()
        } else {
            Logger.e(TAG, "could not enqueue readyPhy command")
        }
        return result
    }

    /**
     * Asynchronous method to clear the services cache. Make sure to add a delay when using this!
     *
     * @return true if the method was executed, false if not executed
     */
    fun clearServicesCache(): Boolean {
        if (bluetoothGatt == null) return false
        var result = false
        try {
            val refreshMethod = bluetoothGatt!!.javaClass.getMethod("refresh")
            if (refreshMethod != null) {
                result = refreshMethod.invoke(bluetoothGatt) as Boolean
            }
        } catch (e: Exception) {
            Logger.e(TAG, "could not invoke refresh method")
        }
        return result
    }

    /**
     * The current command has been completed, move to the next command in the queue (if any)
     */
    private fun completedCommand() {
        isRetrying = false
        commandQueue.poll()
        commandQueueBusy = false
        nextCommand()
    }

    /**
     * Retry the current command. Typically used when a read/write fails and triggers a bonding procedure
     */
    private fun retryCommand() {
        commandQueueBusy = false
        val currentCommand = commandQueue.peek()
        if (currentCommand != null) {
            if (nrTries >= MAX_TRIES) {
                // Max retries reached, give up on this one and proceed
                Logger.d(TAG, "max number of tries reached, not retrying operation anymore")
                commandQueue.poll()
            } else {
                isRetrying = true
            }
        }
        nextCommand()
    }

    /**
     * Execute the next command in the subscribe queue.
     * A queue is used because the calls have to be executed sequentially.
     * If the read or write fails, the next command in the queue is executed.
     */
    private fun nextCommand() {
        synchronized(this) {

            // If there is still a command being executed, then bail out
            if (commandQueueBusy) return

            // Check if there is something to do at all
            val bluetoothCommand = commandQueue.peek() ?: return

            // Check if we still have a valid gatt object
            if (bluetoothGatt == null) {
                Logger.e(
                    TAG,
                    "gatt is 'null' for peripheral '%s', clearing command queue",
                    getAddress()
                )
                commandQueue.clear()
                commandQueueBusy = false
                return
            }

            // Check if the peripheral has initiated bonding as this may be a reason for failures
            if (getBondState() === BondState.BONDING) {
                Logger.w(
                    TAG,
                    "bonding is in progress, waiting for bonding to complete"
                )
                peripheralInitiatedBonding = true
                return
            }

            // Execute the next command in the queue
            commandQueueBusy = true
            if (!isRetrying) {
                nrTries = 0
            }
            mainHandler.post {
                try {
                    bluetoothCommand.run()
                } catch (ex: Exception) {
                    Logger.e(
                        TAG,
                        "command exception for device '%s'",
                        getName()
                    )
                    Logger.e(TAG, ex.toString())
                    completedCommand()
                }
            }
        }
    }

    private fun pairingVariantToString(variant: Int): String {
        return when (variant) {
            PAIRING_VARIANT_PIN -> "PAIRING_VARIANT_PIN"
            PAIRING_VARIANT_PASSKEY -> "PAIRING_VARIANT_PASSKEY"
            PAIRING_VARIANT_PASSKEY_CONFIRMATION -> "PAIRING_VARIANT_PASSKEY_CONFIRMATION"
            PAIRING_VARIANT_CONSENT -> "PAIRING_VARIANT_CONSENT"
            PAIRING_VARIANT_DISPLAY_PASSKEY -> "PAIRING_VARIANT_DISPLAY_PASSKEY"
            PAIRING_VARIANT_DISPLAY_PIN -> "PAIRING_VARIANT_DISPLAY_PIN"
            PAIRING_VARIANT_OOB_CONSENT -> "PAIRING_VARIANT_OOB_CONSENT"
            else -> "UNKNOWN"
        }
    }

    interface InternalCallback {
        /**
         * Trying to connect to [BluetoothPeripheral]
         *
         * @param peripheral [BluetoothPeripheral] the peripheral.
         */
        fun connecting(peripheral: BluetoothPeripheral)

        /**
         * [BluetoothPeripheral] has successfully connected.
         *
         * @param peripheral [BluetoothPeripheral] that connected.
         */
        fun connected(peripheral: BluetoothPeripheral)

        /**
         * Connecting with [BluetoothPeripheral] has failed.
         *
         * @param peripheral [BluetoothPeripheral] of which connect failed.
         */
        fun connectFailed(peripheral: BluetoothPeripheral, status: HciStatus)

        /**
         * Trying to disconnect to [BluetoothPeripheral]
         *
         * @param peripheral [BluetoothPeripheral] the peripheral.
         */
        fun disconnecting(peripheral: BluetoothPeripheral)

        /**
         * [BluetoothPeripheral] has disconnected.
         *
         * @param peripheral [BluetoothPeripheral] that disconnected.
         */
        fun disconnected(peripheral: BluetoothPeripheral, status: HciStatus)
        fun getPincode(peripheral: BluetoothPeripheral): String?
    }

    /////////////////
    private fun connectGattHelper(
        remoteDevice: BluetoothDevice?,
        autoConnect: Boolean,
        bluetoothGattCallback: BluetoothGattCallback
    ): BluetoothGatt? {
        if (remoteDevice == null) {
            return null
        }

        /*
          This bug workaround was taken from the Polidea RxAndroidBle
          Issue that caused a race condition mentioned below was fixed in 7.0.0_r1
          https://android.googlesource.com/platform/frameworks/base/+/android-7.0.0_r1/core/java/android/bluetooth/BluetoothGatt.java#649
          compared to
          https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r72/core/java/android/bluetooth/BluetoothGatt.java#739
          issue: https://android.googlesource.com/platform/frameworks/base/+/d35167adcaa40cb54df8e392379dfdfe98bcdba2%5E%21/#F0
          */return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || !autoConnect) {
            connectGattCompat(bluetoothGattCallback, remoteDevice, autoConnect)
        } else try {
            val iBluetoothGatt = getIBluetoothGatt(getIBluetoothManager())
            if (iBluetoothGatt == null) {
                Logger.e(TAG, "could not get iBluetoothGatt object")
                return connectGattCompat(bluetoothGattCallback, remoteDevice, true)
            }
            val bluetoothGatt = createBluetoothGatt(iBluetoothGatt, remoteDevice)
            if (bluetoothGatt == null) {
                Logger.e(TAG, "could not create BluetoothGatt object")
                return connectGattCompat(bluetoothGattCallback, remoteDevice, true)
            }
            val connectedSuccessfully =
                connectUsingReflection(remoteDevice, bluetoothGatt, bluetoothGattCallback, true)
            if (!connectedSuccessfully) {
                Logger.i(TAG, "connection using reflection failed, closing gatt")
                bluetoothGatt.close()
            }
            bluetoothGatt
        } catch (exception: NoSuchMethodException) {
            Logger.e(TAG, "error during reflection")
            connectGattCompat(bluetoothGattCallback, remoteDevice, true)
        } catch (exception: IllegalAccessException) {
            Logger.e(TAG, "error during reflection")
            connectGattCompat(bluetoothGattCallback, remoteDevice, true)
        } catch (exception: IllegalArgumentException) {
            Logger.e(TAG, "error during reflection")
            connectGattCompat(bluetoothGattCallback, remoteDevice, true)
        } catch (exception: InvocationTargetException) {
            Logger.e(TAG, "error during reflection")
            connectGattCompat(bluetoothGattCallback, remoteDevice, true)
        } catch (exception: InstantiationException) {
            Logger.e(TAG, "error during reflection")
            connectGattCompat(bluetoothGattCallback, remoteDevice, true)
        } catch (exception: NoSuchFieldException) {
            Logger.e(TAG, "error during reflection")
            connectGattCompat(bluetoothGattCallback, remoteDevice, true)
        }
    }

    private fun connectGattCompat(
        bluetoothGattCallback: BluetoothGattCallback,
        device: BluetoothDevice,
        autoConnect: Boolean
    ): BluetoothGatt {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return device.connectGatt(context, autoConnect, bluetoothGattCallback, transport.value)
        } else {
            // Try to call connectGatt with transport parameter using reflection
            try {
                val connectGattMethod = device.javaClass.getMethod(
                    "connectGatt",
                    Context::class.java,
                    Boolean::class.javaPrimitiveType,
                    BluetoothGattCallback::class.java,
                    Int::class.javaPrimitiveType
                )
                try {
                    return connectGattMethod.invoke(
                        device,
                        context,
                        autoConnect,
                        bluetoothGattCallback,
                        transport.value
                    ) as BluetoothGatt
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                } catch (e: InvocationTargetException) {
                    e.printStackTrace()
                }
            } catch (e: NoSuchMethodException) {
                e.printStackTrace()
            }
        }
        // Fallback on connectGatt without transport parameter
        return device.connectGatt(context, autoConnect, bluetoothGattCallback)
    }

    @Throws(
        NoSuchMethodException::class,
        InvocationTargetException::class,
        IllegalAccessException::class,
        NoSuchFieldException::class
    )
    private fun connectUsingReflection(
        device: BluetoothDevice,
        bluetoothGatt: BluetoothGatt,
        bluetoothGattCallback: BluetoothGattCallback,
        autoConnect: Boolean
    ): Boolean {
        setAutoConnectValue(bluetoothGatt, autoConnect)
        val connectMethod = bluetoothGatt.javaClass.getDeclaredMethod(
            "connect",
            Boolean::class.java,
            BluetoothGattCallback::class.java
        )
        connectMethod.isAccessible = true
        return connectMethod.invoke(bluetoothGatt, true, bluetoothGattCallback) as Boolean
    }

    @Throws(
        IllegalAccessException::class,
        InvocationTargetException::class,
        InstantiationException::class
    )
    private fun createBluetoothGatt(
        iBluetoothGatt: Any,
        remoteDevice: BluetoothDevice
    ): BluetoothGatt {
        val bluetoothGattConstructor =
            BluetoothGatt::class.java.declaredConstructors[0]
        bluetoothGattConstructor.isAccessible = true
        return if (bluetoothGattConstructor.parameterTypes.size == 4) {
            bluetoothGattConstructor.newInstance(
                context,
                iBluetoothGatt,
                remoteDevice,
                transport.value
            ) as BluetoothGatt
        } else {
            bluetoothGattConstructor.newInstance(
                context,
                iBluetoothGatt,
                remoteDevice
            ) as BluetoothGatt
        }
    }

    @Throws(
        NoSuchMethodException::class,
        InvocationTargetException::class,
        IllegalAccessException::class
    )
    private fun getIBluetoothGatt(iBluetoothManager: Any?): Any? {
        if (iBluetoothManager == null) {
            return null
        }
        val getBluetoothGattMethod =
            getMethodFromClass(iBluetoothManager.javaClass, "getBluetoothGatt")
        return getBluetoothGattMethod.invoke(iBluetoothManager)
    }

    @Throws(
        NoSuchMethodException::class,
        InvocationTargetException::class,
        IllegalAccessException::class
    )
    private fun getIBluetoothManager(): Any? {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        val getBluetoothManagerMethod =
            getMethodFromClass(bluetoothAdapter.javaClass, "getBluetoothManager")
        return getBluetoothManagerMethod.invoke(bluetoothAdapter)
    }

    @Throws(NoSuchMethodException::class)
    private fun getMethodFromClass(cls: Class<*>, methodName: String): Method {
        val method = cls.getDeclaredMethod(methodName)
        method.isAccessible = true
        return method
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun setAutoConnectValue(bluetoothGatt: BluetoothGatt, autoConnect: Boolean) {
        val autoConnectField = bluetoothGatt.javaClass.getDeclaredField("mAutoConnect")
        autoConnectField.isAccessible = true
        autoConnectField.setBoolean(bluetoothGatt, autoConnect)
    }

    private fun startConnectionTimer(peripheral: BluetoothPeripheral) {
        cancelConnectionTimer()
        timeoutRunnable = Runnable {
            Logger.e(TAG, "connection timout, disconnecting '%s'", peripheral.getName())
            disconnect()
            mainHandler.postDelayed({
                bluetoothGattCallback.onConnectionStateChange(
                    bluetoothGatt,
                    HciStatus.CONNECTION_FAILED_ESTABLISHMENT.value,
                    BluetoothProfile.STATE_DISCONNECTED
                )
            }, 50)
            timeoutRunnable = null
        }
        mainHandler.postDelayed(timeoutRunnable, CONNECTION_TIMEOUT_IN_MS.toLong())
    }

    private fun cancelConnectionTimer() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable!!)
            timeoutRunnable = null
        }
    }

    private fun getTimoutThreshold(): Int {
        val manufacturer = Build.MANUFACTURER
        return if (manufacturer.equals("samsung", ignoreCase = true)) {
            TIMEOUT_THRESHOLD_SAMSUNG
        } else {
            TIMEOUT_THRESHOLD_DEFAULT
        }
    }

    /**
     * Make a safe copy of a nullable byte array
     *
     * @param source byte array to copy
     * @return non-null copy of the source byte array or an empty array if source was null
     */
    fun copyOf(@Nullable source: ByteArray?): ByteArray {
        return if (source == null) ByteArray(0) else Arrays.copyOf(source, source.size)
    }

    /**
     * Make a byte array nonnull by either returning the original byte array if non-null or an empty bytearray
     *
     * @param source byte array to make nonnull
     * @return the source byte array or an empty array if source was null
     */
    fun nonnullOf(@Nullable source: ByteArray?): ByteArray {
        return source ?: ByteArray(0)
    }

    companion object {
        private val TAG = BluetoothPeripheral::class.java.simpleName
        private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * Max MTU that Android can handle
         */
        const val MAX_MTU = 517

        // Minimal and default MTU
        private const val DEFAULT_MTU = 23

        // Maximum number of retries of commands
        private const val MAX_TRIES = 2

        // Delay to use when doing a connect
        private const val DIRECT_CONNECTION_DELAY_IN_MS = 100

        // Timeout to use if no callback on onConnectionStateChange happens
        private const val CONNECTION_TIMEOUT_IN_MS = 35000

        // Samsung phones time out after 5 seconds while most other phone time out after 30 seconds
        private const val TIMEOUT_THRESHOLD_SAMSUNG = 4500

        // Most other phone time out after 30 seconds
        private const val TIMEOUT_THRESHOLD_DEFAULT = 25000

        // When a bond is lost, the bluetooth stack needs some time to update its internal state
        private const val DELAY_AFTER_BOND_LOST = 1000L

        // The average time it takes to complete requestConnectionPriority
        private const val AVG_REQUEST_CONNECTION_PRIORITY_DURATION: Long = 500
        private const val NO_VALID_SERVICE_UUID_PROVIDED = "no valid service UUID provided"
        private const val NO_VALID_CHARACTERISTIC_UUID_PROVIDED =
            "no valid characteristic UUID provided"
        private const val NO_VALID_CHARACTERISTIC_PROVIDED = "no valid characteristic provided"
        private const val NO_VALID_WRITE_TYPE_PROVIDED = "no valid writeType provided"
        private const val NO_VALID_VALUE_PROVIDED = "no valid value provided"
        private const val NO_VALID_DESCRIPTOR_PROVIDED = "no valid descriptor provided"
        private const val NO_VALID_PERIPHERAL_CALLBACK_PROVIDED =
            "no valid peripheral callback provided"
        private const val NO_VALID_DEVICE_PROVIDED = "no valid device provided"
        private const val NO_VALID_PRIORITY_PROVIDED = "no valid priority provided"
        private const val PERIPHERAL_NOT_CONNECTED = "peripheral not connected"
        private const val VALUE_BYTE_ARRAY_IS_EMPTY = "value byte array is empty"
        private const val VALUE_BYTE_ARRAY_IS_TOO_LONG = "value byte array is too long"

        // String constants for commands where the callbacks can also happen because the remote peripheral initiated the command
        private const val IDLE = 0
        private const val REQUEST_MTU_COMMAND = 1
        private const val SET_PHY_TYPE_COMMAND = 2
        private const val PAIRING_VARIANT_PIN = 0
        private const val PAIRING_VARIANT_PASSKEY = 1
        private const val PAIRING_VARIANT_PASSKEY_CONFIRMATION = 2
        private const val PAIRING_VARIANT_CONSENT = 3
        private const val PAIRING_VARIANT_DISPLAY_PASSKEY = 4
        private const val PAIRING_VARIANT_DISPLAY_PIN = 5
        private const val PAIRING_VARIANT_OOB_CONSENT = 6
    }

    /**
     * Constructs a new device wrapper around `device`.
     *
     * @param context  Android application environment.
     * @param device   Wrapped Android bluetooth device.
     * @param listener Callback to [BluetoothCentralManager].
     * @param transport Transport to be used during connection phase.
     */
    init {
        this.context = Objects.requireNonNull(context, "no valid context provided")
        this.device = Objects.requireNonNull(device, NO_VALID_DEVICE_PROVIDED)
        this.listener = Objects.requireNonNull(listener, "no valid listener provided")
        this.peripheralCallback =
            Objects.requireNonNull(peripheralCallback, NO_VALID_PERIPHERAL_CALLBACK_PROVIDED)
        this.callbackHandler =
            Objects.requireNonNull(callbackHandler, "no valid callback handler provided")
        this.transport = Objects.requireNonNull(transport, "no valid transport provided")
    }
}