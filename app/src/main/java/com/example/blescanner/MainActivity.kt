package com.example.blescanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var sensorBleClient: SensorBleClient

    private lateinit var statusText: TextView
    private lateinit var testHttpButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val allGranted =
                permissions.values.all { it }

            if (allGranted) {
                statusText.text =
                    "Permission OK\nReady to scan"

                startButton.isEnabled = true
                testHttpButton.isEnabled = true

                fetchLocation()

            } else {
                statusText.text =
                    "Bluetooth / Location permission required"

                startButton.isEnabled = false
                testHttpButton.isEnabled = false
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        statusText =
            findViewById(R.id.statusText)

        testHttpButton =
            findViewById(R.id.testHttpButton)

        startButton =
            findViewById(R.id.startButton)

        stopButton =
            findViewById(R.id.stopButton)

        sensorBleClient =
            SensorBleClient(this)

        sensorBleClient.listener =
            object :
                SensorBleClient.OnDataTransmittedListener {

                override fun onDataTransmitted(
                    sensorData: SensorData,
                    urlUsed: String,
                    isSuccess: Boolean,
                    responseOrError: String
                ) {
                    runOnUiThread {

                        val statusHeader =
                            if (isSuccess) {
                                "HTTP TRANSMISSION SUCCESS"
                            } else {
                                "HTTP TRANSMISSION ERROR"
                            }

                        val responseDetails =
                            if (isSuccess) {
                                parseAndFormatResponse(
                                    responseOrError
                                )
                            } else {
                                responseOrError
                            }

                        statusText.text =
                            """
                            [$statusHeader]

                            URL:
                            $urlUsed

                            Sensor:
                            ${sensorData.sensor}

                            MAC:
                            ${sensorData.mac}

                            Temp:
                            ${sensorData.temp} °C

                            Humidity:
                            ${sensorData.humidity} %

                            AQI:
                            ${sensorData.aqi}

                            TVOC:
                            ${sensorData.tvoc} ppb

                            eCO2:
                            ${sensorData.eco2} ppm

                            Timestamp:
                            ${sensorData.timestamp}

                            Location:
                            ${sensorData.lat}, ${sensorData.lon}

                            --- Server Response ---
                            $responseDetails
                            """.trimIndent()
                    }
                }

                override fun onRawDataReceived(
                    macAddress: String,
                    rawHex: String
                ) {
                    runOnUiThread {

                        statusText.text =
                            """
                            BLE SENSOR DETECTED

                            MAC:
                            $macAddress

                            13-byte ServiceData:
                            $rawHex

                            Sending to server...
                            """.trimIndent()
                    }
                }
            }

        startButton.isEnabled = false
        testHttpButton.isEnabled = false

        /*
         * 실제 센서 없이 서버 POST만 시험하는 버튼.
         *
         * 현재는 정확한 Team 7 key가 확인되지 않았으므로
         * 임의 데이터를 서버로 보내지 않게 막아둠.
         */
        testHttpButton.setOnClickListener {

            statusText.text = "Sending test data to server..."

            val testData = SensorData(
                key = "opensrc-team 7",
                sender = "android-test",
                sensor = "team7 sensor",
                mac = "00:11:22:33:44:55",
                temp = 24.1,
                humidity = 48.0,
                aqi = 2,
                tvoc = 90,
                eco2 = 620,
                timestamp = System.currentTimeMillis() / 1000,
                lat = 0.0,
                lon = 0.0
            )

            HttpApiClient().sendSensorData(
                testData,
                object : HttpApiClient.HttpCallback {

                    override fun onSuccess(
                        urlUsed: String,
                        responseBody: String
                    ) {
                        runOnUiThread {
                            statusText.text =
                                """
                        SERVER UPLOAD SUCCESS

                        URL:
                        $urlUsed

                        Response:
                        $responseBody
                        """.trimIndent()
                        }
                    }

                    override fun onError(
                        urlUsed: String,
                        statusCode: Int,
                        errorBody: String
                    ) {
                        runOnUiThread {
                            statusText.text =
                                """
                        SERVER UPLOAD FAILED

                        URL:
                        $urlUsed

                        Status:
                        $statusCode

                        Error:
                        $errorBody
                        """.trimIndent()
                        }
                    }
                }
            )
        }

        startButton.setOnClickListener {

            if (!isBluetoothEnabled()) {

                val intent =
                    Intent(
                        BluetoothAdapter.ACTION_REQUEST_ENABLE
                    )

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    startActivity(intent)
                }


            } else {

                fetchLocation()

                sensorBleClient.startScan()

                statusText.text =
                    """
                    BLE SCANNING...

                    Waiting for 13-byte sensor ServiceData...
                    """.trimIndent()
            }
        }

        stopButton.setOnClickListener {

            sensorBleClient.stopScan()

            statusText.text =
                "BLE scan stopped"
        }

        requestPermissions()
    }

    private fun fetchLocation() {

        sensorBleClient.currentLat = 0.0
        sensorBleClient.currentLon = 0.0
    }

    private fun parseAndFormatResponse(
        responseJson: String
    ): String {

        return try {

            val json =
                JSONObject(responseJson)

            val result =
                json.optString(
                    "result",
                    "N/A"
                )

            val message =
                json.optString(
                    "message",
                    ""
                )

            val verified =
                if (json.has("verified")) {
                    json.opt("verified")
                        ?.toString()
                        ?: "N/A"
                } else {
                    "N/A"
                }

            """
            Result: $result
            Message: $message
            Verified: $verified
            """.trimIndent()

        } catch (_: Exception) {

            responseJson
        }
    }

    private fun requestPermissions() {

        val permissions =
            mutableListOf<String>()

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            permissions.add(
                Manifest.permission.BLUETOOTH_SCAN
            )

            permissions.add(
                Manifest.permission.BLUETOOTH_CONNECT
            )
        }

        permissions.add(
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        permissions.add(
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        permissionLauncher.launch(
            permissions.toTypedArray()
        )
    }

    private fun isBluetoothEnabled(): Boolean {

        val bluetoothManager =
            getSystemService(
                BLUETOOTH_SERVICE
            ) as BluetoothManager

        return bluetoothManager
            .adapter
            ?.isEnabled == true
    }

    override fun onDestroy() {

        sensorBleClient.stopScan()

        super.onDestroy()
    }
}