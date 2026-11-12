package com.chue.ytkeep

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object InnerTubeClient {
    private const val KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val CONTEXT =
        """{"client":{"clientName":"WEB","clientVersion":"2.20260101.00.00","hl":"th","gl":"TH"}}"""
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()

    fun context(): JSONObject = JSONObject(CONTEXT)

    fun post(path: String, body: JSONObject): JSONObject {
        return http.newCall(request(path, body)).execute().use(::parseResponse)
    }

    suspend fun postCancellable(path: String, body: JSONObject): JSONObject =
        suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request(path, body))
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: okhttp3.Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    val result = try {
                        response.use(::parseResponse)
                    } catch (error: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                        return
                    } catch (error: IllegalStateException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                        return
                    } catch (error: JSONException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                        return
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }

    private fun request(path: String, body: JSONObject): Request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/$path?key=$KEY&prettyPrint=false")
            .header("User-Agent", UA)
            .post(body.toString().toRequestBody(json))
            .build()

    private fun parseResponse(response: Response): JSONObject {
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("YouTube returned HTTP ${response.code}")
        }
        return JSONObject(text)
    }
}
