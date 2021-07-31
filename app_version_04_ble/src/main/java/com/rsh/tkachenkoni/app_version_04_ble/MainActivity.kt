package com.rsh.tkachenkoni.app_version_04_ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    companion object{
        private val TAG = "BLESCANNER_WORK"
        val BLUETOOTH_REQUEST_CODE = 1

    }

    private val bluetoothAdapter : BluetoothAdapter by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btn_press_start.setOnClickListener {
            if(bluetoothAdapter.isEnabled){
                //Start Scanning
                startBleScan()

            }else{
                Log.d(TAG, "Bluetooth is off")
                val btIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(btIntent, BLUETOOTH_REQUEST_CODE)

            }
        }

    }

    override fun onResume(){
        super.onResume()


    }

    fun startBleScan(){
        Log.d(TAG, "StartBLEScan")
        val scanFilter = ScanFilter.Builder().build()
        val scanFilters : MutableList<ScanFilter> = mutableListOf()
        scanFilters.add(scanFilter)

        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        Log.d(TAG, "Start Scan")
        bluetoothAdapter.bluetoothLeScanner.startScan(scanFilters, scanSettings, bleScanCallback)

    }

    private val bleScanCallback : ScanCallback by lazy {
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

}