package com.rsh.tkachenkoni.app_version_05_ble_02

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.util.*


class MainActivity : AppCompatActivity() {

    var adapter = BluetoothAdapter.getDefaultAdapter()
    var scanner = adapter.bluetoothLeScanner

    val TAG = "BLESCANNER_WORK"
    val ACCESS_COARSE_LOCATION_REQUEST = 1
    val REQUEST_ENABLE_BT = 2


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //setupProgram()

    }

    override fun onResume() {
        super.onResume()

        setupUI()
    }

    fun setupUI(){
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }


    private fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (applicationContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                    ACCESS_COARSE_LOCATION_REQUEST
                )
                return false
            }
        }
        return true
    }


    val scannedDevices : SortedSet<BluetoothDevice> = TreeSet<BluetoothDevice>()
    val scanCallBack: ScanCallback by lazy {
        object : ScanCallback() {

            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                //super.onScanResult(callbackType, result)
                Log.d(TAG, "onScanResult")

                val bluetoothDevice = result?.device
                Log.d(TAG, "bluetoothDevice.exist = " + (bluetoothDevice != null))
                if(bluetoothDevice != null){
                    Log.d(TAG, "======================")
                    Log.d(TAG, "Device Name:  ${result!!.device.name}")
                    Log.d(TAG, "Device Address:  ${result!!.device.address}")
                    scannedDevices.add(bluetoothDevice)
                }
            }
            override fun onBatchScanResults(results: List<ScanResult?>?) {
                // Ignore for now
                Log.d(TAG, "onBatchScanResults")
                Log.d(TAG, "results?.size = " + results?.size)
            }

            override fun onScanFailed(errorCode: Int) {
                // Ignore for now
                Log.d(TAG, "onScanFailed")
                Log.d(TAG, "errorCode = " + errorCode)

            }

        }

    }

    val scanDurationms = 4000L
    //Called after bluetoothleScanner.startScan
    val handlerStopScan = Handler(Looper.getMainLooper()).postDelayed(Runnable {
        scanner.startScan(scanCallBack)
    }, scanDurationms)

    //Connecting
    //resieve command response and information from device server
    val serverCallback : BluetoothGattCallback = object : BluetoothGattCallback(){
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if(BluetoothProfile.STATE_CONNECTED == newState){


            }

        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
        }
    }





    //example all methods
    private val myCallBack: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            //BluetoothGattCharacteristic tempChar = null;
            //tempChar.setValue("test");
            if (status == 0) {
                gatt.connect()
                //gatt.disconnect();
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            // Try to send some data to the device
            characteristic.setValue("test")
            gatt.writeCharacteristic(characteristic)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorRead(gatt, descriptor, status)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
        }

        override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
            super.onReliableWriteCompleted(gatt, status)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            super.onReadRemoteRssi(gatt, rssi, status)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
        }

        override fun equals(o: Any?): Boolean {
            return super.equals(o)
        }

        @Throws(Throwable::class)
        protected fun finalize() {

        }

        override fun hashCode(): Int {
            return super.hashCode()
        }

        override fun toString(): String {
            return super.toString()
        }
    }
    //retain used for all  future device commands
    fun connectDevice(device: BluetoothDevice, context: Context){
        val server : BluetoothGatt = device.connectGatt(context, false, serverCallback)
    }












    //https://habr.com/ru/post/536392/
    fun setupProgram(){





//        val scanSettings = ScanSettings.Builder()
//            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
//            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
//            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
//            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
//            .setReportDelay(0L)
//            .build()
//        if (scanner != null) {
//            scanner.startScan(filters, scanSettings, scanCallback);
//            Log.d(TAG, "scan started");
//        }  else {
//            Log.e(TAG, "could not get scanner object");
//        }

//        // Get device object for a mac address
//        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(peripheralAddress)
//// Check if the peripheral is cached or not
//        int deviceType = device.getType();
//        if(deviceType == BluetoothDevice.DEVICE_TYPE_UNKNOWN) {
//            // The peripheral is not cached
//        } else {
//            // The peripheral is cached
//        }

    }

//    fun scanBloodPressure(){
//        val BLP_SERVICE_UUID: UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")
//        val serviceUUIDs: Array<UUID> = arrayOf<UUID>(BLP_SERVICE_UUID)
//        var filters: MutableList<ScanFilter?>? = null
//        if (serviceUUIDs != null) {
//            filters = ArrayList()
//            for (serviceUUID in serviceUUIDs) {
//                val filter = ScanFilter.Builder()
//                    .setServiceUuid(ParcelUuid(serviceUUID))
//                    .build()
//                filters.add(filter)
//            }
//        }
//        scanner.startScan(filters, scanSettings, scanCallback)
//
//        // Обратите внимание на короткий UUID (например 1810), он называется 16-bit UUID и
//        // является частью длинного 128-bit UUID (в данном случае 00001810-000000-1000-8000-000-00805f9b34fb).
//    }
//
//    fun scanBloodPressure02(){
//
//        val names = arrayOf("Polar H7 391BB014")
//        var filters: MutableList<ScanFilter?>? = null
//        if (names != null) {
//            filters = ArrayList()
//            for (name in names) {
//                val filter = ScanFilter.Builder()
//                    .setDeviceName(name)
//                    .build()
//                filters.add(filter)
//            }
//        }
//        scanner.startScan(filters, scanSettings, scanCallback)
//
//    }
//
//    fun scanBloodPressureMac03(){
//        val peripheralAddresses = arrayOf("01:0A:5C:7D:D0:1A")
//        // Build filters list
//        // Build filters list
//        var filters: MutableList<ScanFilter?>? = null
//        if (peripheralAddresses != null) {
//            filters = ArrayList()
//            for (address in peripheralAddresses) {
//                val filter = ScanFilter.Builder()
//                    .setDeviceAddress(address)
//                    .build()
//                filters.add(filter)
//            }
//        }
//        scanner.startScan(filters, scanSettings, scanByServiceUUIDCallback)
//
//    }


}