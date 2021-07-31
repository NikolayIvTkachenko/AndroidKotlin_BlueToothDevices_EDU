package com.rsh.tkachenkoni.app_version_2

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    val ACTION_REQUEST_ENABLE_BT: Int = 1
    val ACTION_REQUEST_DISCOVERABLE_BT: Int = 2

    lateinit var bluetoothAdapter: BluetoothAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        setupUI()
    }


    fun setupUI() {
        if (bluetoothAdapter == null) {
            tv_bluetooth_status.text = "Bluetooth is not available"
        } else {
            tv_bluetooth_status.text = "Bluetooth is available"
        }

        if (bluetoothAdapter.isEnabled) {
            tv_bluetooth_enable.text = "Bluetooh is enabled"
        } else {
            tv_bluetooth_enable.text = "Bluetooh is not enabled"
        }

        btn_turn_on.setOnClickListener {
            if (bluetoothAdapter.isEnabled) {
                getMessage("Already on")
                tv_bluetooth_enable.text = "Bluetooth is on"
            } else {
                var intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(intent, ACTION_REQUEST_ENABLE_BT)
            }
        }

        btn_turn_off.setOnClickListener {
            if (!bluetoothAdapter.isEnabled) {
                getMessage("Already off")
            } else {
                bluetoothAdapter.disable()
                tv_bluetooth_enable.text = "Bluetooth is off"
                getMessage("Bluetooth turned off")
            }
        }

        btn_discoverable.setOnClickListener {
            if (!bluetoothAdapter.isDiscovering) {
                getMessage("Making Your divice discoverable")

                var intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                startActivityForResult(intent, ACTION_REQUEST_DISCOVERABLE_BT)
            }
        }

        btn_get_pared.setOnClickListener {
            if (bluetoothAdapter.isEnabled) {
                tv_pared_devoces.text = "Paired Devoces"
                val devices = bluetoothAdapter.bondedDevices
                for(device in devices){
                    val deviceName = device.name
                    val deviceAddress = device.address
                    tv_pared_devoces.append("\nDevice: $deviceName $deviceAddress")
                }
            } else {
                getMessage("Turn on bluetooth first")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        when (requestCode) {
            ACTION_REQUEST_ENABLE_BT ->
                if (requestCode == Activity.RESULT_OK) {
                    tv_bluetooth_enable.text = "Bluetooth On"
                    getMessage("Bluetooth is on")
                } else {
                    getMessage("Could not on bluetooth")
                }
//            ACTION_REQUEST_DISCOVERABLE_BT ->
//                if (requestCode == Activity.RESULT_OK) {
//                    tv_bluetooth_enable.text = "Bluetooth On"
//                    getMessage("Bluetooth is on")
//                } else {
//                    getMessage("Could not on bluetooth")
//                }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }


    fun getMessage(str: String) {
        Toast.makeText(this, str, Toast.LENGTH_LONG).show()
    }
}