package com.example.blescanner

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SensorDataParser {

    fun parse(
        bytes: ByteArray,
        sender: String,
        sensorName: String,
        macAddress: String,
        teamKey: String
    ): SensorData? {

        // 5주차 BLE ServiceData는 정확히 13 bytes
        if (bytes.size != 13) {
            Log.w(
                "SensorDataParser",
                "Ignored packet: ${bytes.size} bytes (expected 13 bytes)"
            )
            return null
        }

        val buffer = ByteBuffer
            .wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)

        // 0~1 : Temp (signed int16, ×100)
        val rawTemp = buffer.short
        val temp = rawTemp / 100.0

        // 2~3 : Humidity (uint16, ×100)
        val rawHumidity =
            buffer.short.toInt() and 0xFFFF
        val humidity = rawHumidity / 100.0

        // 4 : AQI (uint8)
        val aqi =
            buffer.get().toInt() and 0xFF

        // 5~6 : TVOC (uint16, ppb)
        val tvoc =
            buffer.short.toInt() and 0xFFFF

        // 7~8 : eCO2 (uint16, ppm)
        val eco2 =
            buffer.short.toInt() and 0xFFFF

        // 9~12 : Unix Timestamp (uint32)
        val timestamp =
            buffer.int.toLong() and 0xFFFFFFFFL

        Log.d(
            "SensorDataParser",
            """
            Parsed 13-byte BLE packet
            Temp: $temp °C
            Humidity: $humidity %
            AQI: $aqi
            TVOC: $tvoc ppb
            eCO2: $eco2 ppm
            Timestamp: $timestamp
            """.trimIndent()
        )

        return SensorData(
            key = teamKey,
            sender = sender,
            sensor = sensorName,
            mac = macAddress,
            temp = temp,
            humidity = humidity,
            aqi = aqi,
            tvoc = tvoc,
            eco2 = eco2,
            lat = 0.0,
            lon = 0.0,
            timestamp = timestamp
        )
    }

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") {
            "%02x".format(it.toInt() and 0xFF)
        }
    }
}