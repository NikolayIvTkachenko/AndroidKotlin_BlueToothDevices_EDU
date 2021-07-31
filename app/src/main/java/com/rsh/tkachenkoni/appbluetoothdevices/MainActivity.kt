package com.rsh.tkachenkoni.appbluetoothdevices

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    val TAG = "ANDROID_BLUETOOTH_CODE"

    lateinit var bluetooth: BluetoothAdapter
    val listDevoce  = ArrayList<String>()

    val REQUEST_ENABLE_BT = 101
    var status = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registrationBroadcastBluetooth()


        Log.d(TAG, "onCreate")
        bluetooth = BluetoothAdapter.getDefaultAdapter()
        if(bluetooth != null){
            //BlueTooth all correct
            if(bluetooth.isEnabled){
                //BlueTooth enabled
                val myDeviceAddress = bluetooth.address
                val myDeviceName = bluetooth.name

                status = myDeviceName + ":" + myDeviceAddress
                Log.d(TAG, "before rename status = " + status)
                bluetooth.setName("Android BlueTooth")
                val myState = bluetooth.state

                status = myDeviceName + ":" + myDeviceAddress + ":" + myState
                Log.d(TAG, "after rename status = " + status)



                var pairedDevices: MutableSet<BluetoothDevice>? = bluetooth.bondedDevices
                Log.d(TAG, "pairedDevices.size = " + pairedDevices!!.size)
                if (pairedDevices!!.size > 0){
                    Log.d(TAG, "=================")
                    for (dev in pairedDevices){
                        Log.d(TAG, "----------------")
                        Log.d(TAG, "dev.name = " + dev.name)
                        Log.d(TAG, "dev.address = " + dev.address)
                        Log.d(TAG, "dev.alias = " + dev.alias)
                        Log.d(TAG, "dev.type = " + dev.type)
                        Log.d(TAG, "dev.bondState = " + dev.bondState)
                    }

                }

            }else{
                status = "Bluetooth disable"
                val enableBtIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        }else{
            //Bluetooth not work

        }

        getMessage(status)

    }

    fun getMessage(str: String){
        Toast.makeText(this, str, Toast.LENGTH_LONG).show()
    }


    private val reciever = object : BroadcastReceiver (){
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String = intent?.action.toString()
            Log.d(TAG, "BroadcastReceiver BlueTooth onReceive")
            listDevoce.clear()
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                //Add to list devices
                val device = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                listDevoce.add(device!!.name + " : " + device!!.address)
                Log.d(TAG, "device!!.name = " + device!!.name)
                Log.d(TAG, "device!!.address = " + device!!.address)
            }

        }
    }

    fun registrationBroadcastBluetooth(){
        val filter: IntentFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(reciever,filter)
    }
}