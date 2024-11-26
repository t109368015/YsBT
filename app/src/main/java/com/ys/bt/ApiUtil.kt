package com.ys.bt

import android.util.Log
import com.google.gson.Gson
import com.orango.electronic.orange_og_lib.Util.YsClock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

object ApiUtil {
    data class TeslaUpload(
        val time: String,
        val direction: String,
        val log: String
    )

    data class ApiRes(
        val result: Boolean,
        val data: String?
    )

    fun postRequest(url: String, json: Any, timeOut: Long = 30L): ApiRes {
        val client = OkHttpClient()
        var res = Pair<Boolean?, String?>(null, null)
        val clock = YsClock()

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = Gson().toJson(json).toRequestBody(mediaType)
        Log.e("ApiUtil", "Request: ${url}\n${Gson().toJson(json)}")

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiUtil", "Request failed: $e")
                res = Pair(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                res = if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    Log.e("ApiUtil", "Request Success: $responseData")
                    Pair(true, responseData)
                } else {
                    Log.e("ApiUtil", "Request failed: ${response.code}")
                    Pair(false, null)
                }
            }
        })

        while (res.first == null) {
            if (clock.runTimeS() >= timeOut) {
                Log.e("ApiUtil", "Request TimeOut")
                return ApiRes(false, null)
            }
            Thread.sleep(100)
        }

        return ApiRes(res.first!!, res.second)
    }

    fun postBento(path: String, json: Any): ApiRes {
        return postRequest("https://bento3.orange-electronic.com$path", json)
    }
}
