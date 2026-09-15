package com.example.blescanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter

    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceName: TextView
    private lateinit var tvMac: TextView
    private lateinit var tvRssi: TextView
    private lateinit var tvSensorData: TextView
    private lateinit var tvLog: TextView

    private lateinit var btnScan: Button
    private lateinit var btnStop: Button
    private lateinit var btnSave: Button

    private val collectedData = mutableListOf<String>()

    // 권한 요청 결과 처리
    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val allGranted = permissions.values.all { it }

            if (allGranted) {
                addLog("블루투스 권한이 허용되었습니다.")
                startBleScan()
            } else {
                addLog("블루투스 권한이 필요합니다.")
                tvStatus.text = "●  권한 필요"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI 연결
        tvStatus = findViewById(R.id.tvStatus)
        tvDeviceName = findViewById(R.id.tvDeviceName)
        tvMac = findViewById(R.id.tvMac)
        tvRssi = findViewById(R.id.tvRssi)
        tvSensorData = findViewById(R.id.tvSensorData)
        tvLog = findViewById(R.id.tvLog)

        btnScan = findViewById(R.id.btnScan)
        btnStop = findViewById(R.id.btnStop)
        btnSave = findViewById(R.id.btnSave)

        // BluetoothAdapter 생성
        val bluetoothManager =
            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        bluetoothAdapter = bluetoothManager.adapter

        // 스캔 시작
        btnScan.setOnClickListener {
            checkPermissionAndScan()
        }

        // 스캔 중지
        btnStop.setOnClickListener {
            stopBleScan()
        }

        // CSV는 다음 단계에서 구현
        btnSave.setOnClickListener {
            saveCsvFile()
        }
    }

    // 필요한 권한 확인
    private fun checkPermissionAndScan() {

        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }

        } else {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissions.isEmpty()) {
            startBleScan()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    // BLE 스캔 시작
    @SuppressLint("MissingPermission")
    private fun startBleScan() {

        if (!bluetoothAdapter.isEnabled) {
            tvStatus.text = "●  블루투스가 꺼져 있습니다"
            addLog("휴대폰의 블루투스를 켜주세요.")
            return
        }

        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        if (bluetoothLeScanner == null) {
            addLog("BLE Scanner를 사용할 수 없습니다.")
            return
        }

        bluetoothLeScanner.startScan(scanCallback)

        tvStatus.text = "●  스캔 중"
        addLog("BLE 스캔을 시작했습니다.")
    }

    // BLE 스캔 중지
    @SuppressLint("MissingPermission")
    private fun stopBleScan() {

        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)

        tvStatus.text = "●  스캔 중지"
        addLog("BLE 스캔을 중지했습니다.")
    }

    // BLE 장치가 발견될 때마다 실행
    private val scanCallback = object : ScanCallback() {

        @SuppressLint("MissingPermission")
        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            super.onScanResult(callbackType, result)

            val device = result.device

            val deviceName =
                device.name ?: result.scanRecord?.deviceName ?: "이름 없는 장치"

            val macAddress = device.address
            val rssi = result.rssi

            // 이번 실습에서 찾는 Raspberry Pi만 표시
            if (deviceName == "opensrc_week_3") {

                tvDeviceName.text = deviceName
                tvMac.text = "MAC  $macAddress"
                tvRssi.text = "RSSI  $rssi dBm"

                // 현재는 원본 패킷 표시
                // 0x181A = Environmental Sensing Service
                val serviceUuid =
                    android.os.ParcelUuid.fromString(
                        "0000181A-0000-1000-8000-00805F9B34FB"
                    )

                val sensorData =
                    result.scanRecord?.getServiceData(serviceUuid)

                if (sensorData != null) {

                    val packet = SensorPacket.parse(sensorData)

                    if (packet != null) {
                        tvSensorData.text = packet.toString()

                        val receivedTime = SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss",
                            Locale.getDefault()
                        ).format(Date())

                        val csvRow =
                            "$receivedTime,$deviceName,$macAddress,$rssi," +
                                    "${packet.temperature},${packet.humidity}," +
                                    "${packet.aqi},${packet.tvoc},${packet.eco2},${packet.timestamp}"

                        collectedData.add(csvRow)


                        addLog(
                            "센서 데이터 수신 / " +
                                    "온도 ${packet.temperature}°C / " +
                                    "습도 ${packet.humidity}%"
                        )
                    } else {
                        tvSensorData.text = "패킷 분석 실패"
                        addLog("센서 데이터 길이가 올바르지 않습니다.")
                    }

                } else {
                    tvSensorData.text = "0x181A 센서 데이터 수신 대기 중"
                }

                addLog("$deviceName 발견 / RSSI $rssi dBm")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)

            tvStatus.text = "●  스캔 실패"
            addLog("BLE 스캔 실패 (오류 코드: $errorCode)")
        }
    }

    private fun addLog(message: String) {

        val oldLog = tvLog.text.toString()

        tvLog.text =
            if (oldLog.isBlank()) {
                message
            } else {
                "$message\n$oldLog"
            }
    }
    private fun saveCsvFile() {

        if (collectedData.isEmpty()) {
            addLog("저장할 센서 데이터가 없습니다.")
            return
        }

        try {
            val time = SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.getDefault()
            ).format(Date())

            val fileName = "BLE_Sensor_$time.csv"

            val file = File(
                getExternalFilesDir(null),
                fileName
            )

            FileWriter(file).use { writer ->

                // CSV 첫 번째 줄
                writer.append(
                    "received_time,device_name,mac_address,rssi," +
                            "temperature,humidity,aqi,tvoc,eco2,sensor_timestamp\n"
                )

                // 수집한 센서 데이터
                collectedData.forEach { row ->
                    writer.append(row)
                    writer.append("\n")
                }
            }

            addLog(
                "CSV 저장 완료 (${collectedData.size}개 데이터)\n" +
                        "파일명: $fileName"
            )

        } catch (e: Exception) {
            addLog("CSV 저장 실패: ${e.message}")
        }
    }

}