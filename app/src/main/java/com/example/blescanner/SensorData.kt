package com.example.blescanner

import org.json.JSONObject

data class SensorData(
    val key: String,
    val sender: String,
    val sensor: String,
    val mac: String,
    val temp: Double,
    val humidity: Double,
    val aqi: Int,
    val tvoc: Int,
    val eco2: Int,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val timestamp: Long
) {
    fun toJsonString(): String {
        val json = JSONObject()

        json.put("key", key)
        json.put("sensor", sensor)
        json.put("mac", mac)

        json.put("temp", temp)
        json.put("humidity", humidity)

        json.put("AQI", aqi)
        json.put("TVOC", tvoc)
        json.put("eCO2", eco2)

        json.put("timestamp", timestamp)

        json.put("lat", lat)
        json.put("lon", lon)

        json.put("sender", sender)

        return json.toString()
    }
}
