package com.example.myapplication.core.network

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

object ApiClient {

    private const val BASE_URL = "http://172.31.57.18:8080/nungil-server/api"
    private val client = OkHttpClient()
    private val JSON = "application/json".toMediaType()

    fun get(path: String, onResult: (String?) -> Unit) {
        val request = Request.Builder().url("$BASE_URL$path").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onResult(null)
            override fun onResponse(call: Call, response: Response) =
                onResult(response.body?.string())
        })
    }

    fun post(path: String, body: String, onResult: (String?) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL$path")
            .post(body.toRequestBody(JSON))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onResult(null)
            override fun onResponse(call: Call, response: Response) =
                onResult(response.body?.string())
        })
    }
}