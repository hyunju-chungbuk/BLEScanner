package com.example.blescanner

data class ApiResponse(
    val result: String,
    val message: String,
    val received_data: ReceivedData
)

data class ReceivedData(
    val team: String,
    val sensor: String
)
