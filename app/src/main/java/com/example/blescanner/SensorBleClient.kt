package com.example.blescanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.provider.Settings
import android.util.Log

class SensorBleClient(private val context: Context) {

    interface OnDataTransmittedListener {
        fun onDataTransmitted(
            sensorData: SensorData,
            urlUsed: String,
            isSuccess: Boolean,
            responseOrError: String
        )

        fun onRawDataReceived(
            macAddress: String,
            rawHex: String
        )
    }

    var listener: OnDataTransmittedListener? = null

    var currentLat: Double = 0.0
    var currentLon: Double = 0.0

    private val httpClient = HttpApiClient()

    private val androidId: String by lazy {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "UNKNOWN_DEVICE"
    }

    /*
     * ★ 7팀 실제 키인지는 모름. 확인 필요.
     */
    private val teamKey = "opensrc-team 7"

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE)
                    as? BluetoothManager

        bluetoothManager?.adapter
    }

    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private var isScanning = false

    private val lastProcessedTimestampMap =
        mutableMapOf<String, Long>()

    private val scanCallback =
        object : ScanCallback() {

            @SuppressLint("MissingPermission")
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult?
            ) {
                super.onScanResult(
                    callbackType,
                    result
                )

                val scanResult = result ?: return
                val device = scanResult.device ?: return

                /*
                 * 5주차:
                 * ServiceData에서 13-byte payload 추출
                 */
                val payload =
                    extractSensorPayload(scanResult)
                        ?: return

                if (payload.size != 13) {
                    Log.w(
                        "SensorBleClient",
                        "Ignored packet: ${payload.size} bytes (expected 13)"
                    )
                    return
                }

                val macAddress =
                    device.address
                        ?: "00:00:00:00:00:00"

                val deviceName =
                    try {
                        device.name ?: "Unknown Sensor"
                    } catch (_: SecurityException) {
                        "Unknown Sensor"
                    }

                //BLE local name 설정시 필터링 변경 요망

                if (deviceName != "Opensrc_team7") {
                    return
                }

                val sensorData =
                    SensorDataParser.parse(
                        bytes = payload,
                        sender = androidId,
                        sensorName = deviceName,
                        macAddress = macAddress,
                        teamKey = teamKey
                    ) ?: return

                val finalSensorData =
                    sensorData.copy(
                        lat = currentLat,
                        lon = currentLon
                    )

                /*
                 * 화면 확인용.
                 * 서버 JSON에는 raw를 보내지 않음.
                 */
                listener?.onRawDataReceived(
                    macAddress,
                    SensorDataParser.bytesToHex(payload)
                )

                /*
                 * 같은 센서 데이터를 너무 빠르게
                 * 연속 전송하지 않도록 제한
                 */
                val currentTime =
                    System.currentTimeMillis()

                val lastTime =
                    lastProcessedTimestampMap[
                        macAddress
                    ] ?: 0L

                if (
                    currentTime - lastTime < 2000
                ) {
                    return
                }

                lastProcessedTimestampMap[
                    macAddress
                ] = currentTime

                Log.d(
                    "SensorBleClient",
                    """
                    Sending SensorData
                    Sensor: ${finalSensorData.sensor}
                    MAC: ${finalSensorData.mac}
                    Temp: ${finalSensorData.temp}
                    Humidity: ${finalSensorData.humidity}
                    AQI: ${finalSensorData.aqi}
                    TVOC: ${finalSensorData.tvoc}
                    eCO2: ${finalSensorData.eco2}
                    Timestamp: ${finalSensorData.timestamp}
                    """.trimIndent()
                )

                httpClient.sendSensorData(
                    finalSensorData,
                    object :
                        HttpApiClient.HttpCallback {

                        override fun onSuccess(
                            urlUsed: String,
                            responseBody: String
                        ) {
                            listener?.onDataTransmitted(
                                finalSensorData,
                                urlUsed,
                                true,
                                responseBody
                            )
                        }

                        override fun onError(
                            urlUsed: String,
                            statusCode: Int,
                            errorBody: String
                        ) {
                            listener?.onDataTransmitted(
                                finalSensorData,
                                urlUsed,
                                false,
                                "Status: $statusCode, Msg: $errorBody"
                            )
                        }
                    }
                )
            }

            override fun onScanFailed(
                errorCode: Int
            ) {
                super.onScanFailed(errorCode)

                Log.e(
                    "SensorBleClient",
                    "BLE scan failed: $errorCode"
                )

                isScanning = false
            }
        }

    /*
     * 이번 주 패킷:
     * ServiceData = 13 bytes
     */
    private fun extractSensorPayload(
        result: ScanResult
    ): ByteArray? {

        val scanRecord =
            result.scanRecord ?: return null

        val serviceDataMap =
            scanRecord.serviceData

        if (!serviceDataMap.isNullOrEmpty()) {

            for ((uuid, bytes) in serviceDataMap) {

                Log.d(
                    "SensorBleClient",
                    "ServiceData UUID=$uuid, size=${bytes.size}"
                )

                if (bytes.size == 13) {

                    Log.d(
                        "SensorBleClient",
                        "Valid 13-byte ServiceData found"
                    )

                    return bytes
                }
            }
        }

        return null
    }

    @SuppressLint("MissingPermission")
    fun startScan() {

        if (isScanning) return

        val scanner =
            bluetoothLeScanner

        if (
            scanner != null &&
            bluetoothAdapter?.isEnabled == true
        ) {
            try {

                val scanSettings =
                    ScanSettings.Builder()
                        .setScanMode(
                            ScanSettings
                                .SCAN_MODE_LOW_LATENCY
                        )
                        .build()

                scanner.startScan(
                    null,
                    scanSettings,
                    scanCallback
                )

                isScanning = true

                Log.d(
                    "SensorBleClient",
                    "BLE scan started"
                )

            } catch (e: Exception) {

                Log.e(
                    "SensorBleClient",
                    "Failed to start BLE scan",
                    e
                )
            }

        } else {

            Log.w(
                "SensorBleClient",
                "Bluetooth disabled or scanner unavailable"
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {

        if (!isScanning) return

        val scanner =
            bluetoothLeScanner

        if (scanner != null) {
            try {

                scanner.stopScan(
                    scanCallback
                )

                Log.d(
                    "SensorBleClient",
                    "BLE scan stopped"
                )

            } catch (e: Exception) {

                Log.e(
                    "SensorBleClient",
                    "Failed to stop BLE scan",
                    e
                )
            }
        }

        isScanning = false
    }
}