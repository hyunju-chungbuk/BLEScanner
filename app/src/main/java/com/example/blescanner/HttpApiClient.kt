package com.example.blescanner

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpApiClient {

    private val primaryUrl =
        "http://203.255.81.72:10021/sensor/opensrc/upload/"

    private val campusUrl =
        "http://10.255.81.72:10021/sensor/opensrc/upload/"

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

    interface HttpCallback {
        fun onSuccess(
            urlUsed: String,
            responseBody: String
        )

        fun onError(
            urlUsed: String,
            statusCode: Int,
            errorBody: String
        )
    }

    fun sendSensorData(
        sensorData: SensorData,
        callback: HttpCallback
    ) {
        sendToUrl(
            primaryUrl,
            sensorData,
            callback,
            true
        )
    }

    private fun sendToUrl(
        url: String,
        sensorData: SensorData,
        callback: HttpCallback,
        tryCampusOnFailure: Boolean
    ) {
        val json =
            sensorData.toJsonString()

        Log.d(
            "HttpApiClient",
            "POST $url\n$json"
        )

        val mediaType =
            MediaType.parse(
                "application/json; charset=utf-8"
            )

        val requestBody =
            RequestBody.create(
                mediaType,
                json
            )

        val request =
            Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

        client.newCall(request)
            .enqueue(
                object : Callback {

                    override fun onFailure(
                        call: Call,
                        e: IOException
                    ) {
                        Log.e(
                            "HttpApiClient",
                            "HTTP failed: $url",
                            e
                        )

                        if (tryCampusOnFailure) {
                            sendToUrl(
                                campusUrl,
                                sensorData,
                                callback,
                                false
                            )
                        } else {
                            callback.onError(
                                url,
                                -1,
                                e.message
                                    ?: "Network error"
                            )
                        }
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response
                    ) {
                        val statusCode =
                            response.code()

                        val responseText =
                            response.body()
                                ?.string()
                                ?: ""

                        response.close()

                        if (
                            statusCode in 200..299
                        ) {
                            callback.onSuccess(
                                url,
                                responseText
                            )
                        } else {
                            callback.onError(
                                url,
                                statusCode,
                                responseText
                            )
                        }
                    }
                }
            )
    }
}