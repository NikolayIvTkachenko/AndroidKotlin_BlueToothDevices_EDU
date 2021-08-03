package com.rsh.tkachenkoni.app_version_07_ble_04_example


import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model.BluetoothCentralManager
import com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model.BluetoothPeripheral
import com.rsh.tkachenkoni.app_version_07_ble_04_example.models.*
import timber.log.Timber
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale


class MainActivity : AppCompatActivity() {

    private var measurementValue: TextView? = null
    private val REQUEST_ENABLE_BT = 1
    private val ACCESS_LOCATION_REQUEST = 2
    private val dateFormat: DateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        measurementValue = findViewById(R.id.bloodPressureValue);

        registerReceiver(locationServiceStateReceiver, IntentFilter((LocationManager.MODE_CHANGED_ACTION)));
        registerReceiver(bloodPressureDataReceiver, IntentFilter( BluetoothHandler.MEASUREMENT_BLOODPRESSURE ));
        registerReceiver(temperatureDataReceiver, IntentFilter( BluetoothHandler.MEASUREMENT_TEMPERATURE ));
        registerReceiver(heartRateDataReceiver, IntentFilter( BluetoothHandler.MEASUREMENT_HEARTRATE ));
        registerReceiver(pulseOxDataReceiver, IntentFilter( BluetoothHandler.MEASUREMENT_PULSE_OX ));
        registerReceiver(weightDataReceiver, IntentFilter(BluetoothHandler.MEASUREMENT_WEIGHT));
        registerReceiver(glucoseDataReceiver, IntentFilter(BluetoothHandler.MEASUREMENT_GLUCOSE));
    }
    override fun onResume() {
        super.onResume()
        if (BluetoothAdapter.getDefaultAdapter() != null) {
            if (!isBluetoothEnabled()) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            } else {
                checkPermissions()
            }
        } else {
            Timber.e("This device has no Bluetooth hardware")
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return bluetoothAdapter.isEnabled
    }

    private fun initBluetoothHandler() {
        BluetoothHandler.getInstance(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(locationServiceStateReceiver)
        unregisterReceiver(bloodPressureDataReceiver)
        unregisterReceiver(temperatureDataReceiver)
        unregisterReceiver(heartRateDataReceiver)
        unregisterReceiver(pulseOxDataReceiver)
        unregisterReceiver(weightDataReceiver)
        unregisterReceiver(glucoseDataReceiver)
    }

    private val locationServiceStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            if (action != null && action == LocationManager.MODE_CHANGED_ACTION) {
                val isEnabled = areLocationServicesEnabled()
                Timber.i("Location service state changed to: %s", if (isEnabled) "on" else "off")
                checkPermissions()
            }
        }
    }

    private val bloodPressureDataReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val peripheral =
                getPeripheral(intent.getStringExtra(BluetoothHandler.MEASUREMENT_EXTRA_PERIPHERAL))
            val measurement =
                intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_BLOODPRESSURE_EXTRA) as BloodPressureMeasurement?
                    ?: return
            measurementValue!!.text = java.lang.String.format(
                Locale.ENGLISH,
                "%.0f/%.0f %s, %.0f bpm\n%s\n\nfrom %s",
                measurement.systolic,
                measurement.diastolic,
                if (measurement.isMMHG) "mmHg" else "kpa",
                measurement.pulseRate,
                dateFormat.format(measurement.timestamp),
                peripheral.getName()
            )
        }
    }

    private val temperatureDataReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val peripheral =
                getPeripheral(intent.getStringExtra(BluetoothHandler.MEASUREMENT_EXTRA_PERIPHERAL))
            val measurement =
                intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_TEMPERATURE_EXTRA) as TemperatureMeasurement?
                    ?: return
            measurementValue!!.text = java.lang.String.format(
                Locale.ENGLISH,
                "%.1f %s (%s)\n%s\n\nfrom %s",
                measurement.temperatureValue,
                if (measurement.unit === TemperatureUnit.Celsius) "celsius" else "fahrenheit",
                measurement.type,
                dateFormat.format(measurement.timestamp),
                peripheral.getName()
            )
        }
    }

    private val heartRateDataReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val measurement =
                intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_HEARTRATE_EXTRA) as HeartRateMeasurement?
                    ?: return
            measurementValue!!.text =
                java.lang.String.format(Locale.ENGLISH, "%d bpm", measurement.pulse)
        }
    }

    private val pulseOxDataReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val peripheral =
                getPeripheral(intent.getStringExtra(BluetoothHandler.MEASUREMENT_EXTRA_PERIPHERAL))
            val measurement =
                intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_PULSE_OX_EXTRA_CONTINUOUS) as PulseOximeterContinuousMeasurement?
            if (measurement != null) {
                measurementValue!!.text = java.lang.String.format(
                    Locale.ENGLISH,
                    "SpO2 %d%%,  Pulse %d bpm\n\nfrom %s",
                    measurement.getSpO2(),
                    measurement.getPulseRate(),
                    peripheral.getName()
                )
            }
            val spotMeasurement =
                intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_PULSE_OX_EXTRA_SPOT) as PulseOximeterSpotMeasurement?
            if (spotMeasurement != null) {
                measurementValue!!.text = java.lang.String.format(
                    Locale.ENGLISH,
                    "SpO2 %d%%,  Pulse %d bpm\n%s\n\nfrom %s",
                    spotMeasurement.spO2,
                    spotMeasurement.pulseRate,
                    dateFormat.format(spotMeasurement.timestamp),
                    peripheral.getName()
                )
            }
        }
    }

    private val weightDataReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val peripheral =
                getPeripheral(intent.getStringExtra(BluetoothHandler.MEASUREMENT_EXTRA_PERIPHERAL))
            val measurement =
                intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_WEIGHT_EXTRA) as WeightMeasurement?
            if (measurement != null) {
                measurementValue!!.text = java.lang.String.format(
                    Locale.ENGLISH,
                    "%.1f %s\n%s\n\nfrom %s",
                    measurement.weight,
                    measurement.unit.toString(),
                    dateFormat.format(measurement.timestamp),
                    peripheral.getName()
                )
            }
        }
    }

    private val glucoseDataReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val peripheral =
                getPeripheral(intent.getStringExtra(BluetoothHandler.MEASUREMENT_EXTRA_PERIPHERAL))
            val measurement =
                intent.getSerializableExtra(BluetoothHandler.MEASUREMENT_GLUCOSE_EXTRA) as GlucoseMeasurement?
            if (measurement != null) {
                measurementValue!!.text = java.lang.String.format(
                    Locale.ENGLISH,
                    "%.1f %s\n%s\n\nfrom %s",
                    measurement.value,
                    if (measurement.unit === GlucoseMeasurementUnit.MmolPerLiter) "mmol/L" else "mg/dL",
                    dateFormat.format(measurement.timestamp),
                    peripheral.getName()
                )
            }
        }
    }

    private fun getPeripheral(peripheralAddress: String?): BluetoothPeripheral {
        val central: BluetoothCentralManager =
            BluetoothHandler.getInstance(applicationContext)!!.central
        return central.getPeripheral(peripheralAddress!!)
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val missingPermissions = getMissingPermissions(getRequiredPermissions())
            if (missingPermissions.size > 0) {
                requestPermissions(missingPermissions, ACCESS_LOCATION_REQUEST)
            } else {
                permissionsGranted()
            }
        }
    }

    private fun getMissingPermissions(requiredPermissions: Array<String>): Array<String> {
        val missingPermissions: MutableList<String> = ArrayList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (requiredPermission in requiredPermissions) {
                if (applicationContext.checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(requiredPermission)
                }
            }
        }
        return missingPermissions.toTypedArray()
    }

    private fun getRequiredPermissions(): Array<String> {
        val targetSdkVersion = applicationInfo.targetSdkVersion
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun permissionsGranted() {
        // Check if Location services are on because they are required to make scanning work
        if (checkLocationServices()) {
            initBluetoothHandler()
        }
    }

    private fun areLocationServicesEnabled(): Boolean {
        val locationManager =
            applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (locationManager == null) {
            Timber.e("could not get location manager")
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled =
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            isGpsEnabled || isNetworkEnabled
        }
    }

    private fun checkLocationServices(): Boolean {
        return if (!areLocationServicesEnabled()) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Location services are not enabled")
                .setMessage("Scanning for Bluetooth peripherals requires locations services to be enabled.") // Want to enable?
                .setPositiveButton("Enable",
                    DialogInterface.OnClickListener { dialogInterface, i ->
                        dialogInterface.cancel()
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    })
                .setNegativeButton(
                    "Cancel",
                    DialogInterface.OnClickListener { dialog, which -> // if this button is clicked, just close
                        // the dialog box and do nothing
                        dialog.cancel()
                    })
                .create()
                .show()
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Check if all permission were granted
        var allGranted = true
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false
                break
            }
        }
        if (allGranted) {
            permissionsGranted()
        } else {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Location permission is required for scanning Bluetooth peripherals")
                .setMessage("Please grant permissions")
                .setPositiveButton("Retry",
                    DialogInterface.OnClickListener { dialogInterface, i ->
                        dialogInterface.cancel()
                        checkPermissions()
                    })
                .create()
                .show()
        }
    }
}