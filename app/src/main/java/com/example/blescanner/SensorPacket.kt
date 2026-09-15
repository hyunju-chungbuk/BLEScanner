package com.example.blescanner

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class SensorPacket(
    val temperature: Float,
    val humidity: Float,
    val aqi: Int,
    val tvoc: Int,
    val eco2: Int,
    val timestamp: Long
) {
    companion object {

        fun parse(data: ByteArray): SensorPacket? {

            // 필요한 데이터 길이:
            // uint16 + uint16 + uint8 + uint16 + uint16 + uint32 = 13 bytes
            if (data.size < 13) {
                return null
            }

            val buffer = ByteBuffer.wrap(data)
                .order(ByteOrder.LITTLE_ENDIAN)

            val temperature =
                (buffer.short.toInt() and 0xFFFF) / 100.0f

            val humidity =
                (buffer.short.toInt() and 0xFFFF) / 100.0f

            val aqi =
                buffer.get().toInt() and 0xFF

            val tvoc =
                buffer.short.toInt() and 0xFFFF

            val eco2 =
                buffer.short.toInt() and 0xFFFF

            val timestamp =
                buffer.int.toLong() and 0xFFFFFFFFL

            return SensorPacket(
                temperature,
                humidity,
                aqi,
                tvoc,
                eco2,
                timestamp
            )
        }
    }

    override fun toString(): String {
        return """
            온도: %.2f °C
            습도: %.2f %%
            AQI: %d
            TVOC: %d ppb
            eCO₂: %d ppm
            Timestamp: %d
        """.trimIndent().format(
            temperature,
            humidity,
            aqi,
            tvoc,
            eco2,
            timestamp
        )
    }
}
