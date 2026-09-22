package com.example.blescanner

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("sensor/opensrc/test/")
    fun sendSensorData(
        @Body data: SensorData
    ): Call<ApiResponse>
}
