package com.routewise.sa.data.remote

import com.routewise.sa.model.SerpApiResponse
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class SerpApiService(private val apiKey: String) {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun fetchDirections(
        startAddr: String,
        endAddr: String,
        onSuccess: (SerpApiResponse) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val url = "https://serpapi.com/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("engine", "google_maps_directions")
            .addQueryParameter("start_addr", startAddr)
            .addQueryParameter("end_addr", endAddr)
            .addQueryParameter("api_key", apiKey)
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onError(e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onError(IOException("Unexpected code ${it.code}"))
                        return
                    }
                    val body = it.body?.string() ?: ""
                    try {
                        val parsed = json.decodeFromString<SerpApiResponse>(body)
                        onSuccess(parsed)
                    } catch (e: Exception) {
                        onError(e)
                    }
                }
            }
        })
    }
}
