package com.example.blescanner

data class SensorData(
    val Team: String,
    val Sensor: String,
    val Mac: String,
    val Temp: Double,
    val Humidity: Double,
    val AQI: Int,
    val TVOC: Int,
    val eCO2: Int,
    val Timestamp: Long,
    val Lat: Double,
    val Lon: Double,
    val Sender: String,
    val RSSI: String,
)
