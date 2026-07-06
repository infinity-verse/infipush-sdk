package com.infipush.network

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Lightweight HTTP client wrapping OkHttp — no Retrofit needed.
 * Keeps the SDK dependency footprint small.
 */
object ApiClient {

    private val gson = Gson()

    private fun buildClient(debug: Boolean = false): OkHttpClient {
        return OkHttpClient.Builder().apply {
            if (debug) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
        }.build()
    }

    fun deviceService(baseUrl: String, appId: String): DeviceApiService =
        DeviceApiServiceImpl(buildClient(), gson, baseUrl.trimEnd('/'), appId)

    fun trackingService(baseUrl: String, appId: String): TrackingApiService =
        TrackingApiServiceImpl(buildClient(), baseUrl.trimEnd('/'), appId)
}

// ─── Payload models ──────────────────────────────────────────────────────────

data class DevicePayload(
    val token: String,
    val os: String,
    val deviceModel: String,
    val appVersion: String,
    val tags: Map<String, String>,
    val language: String,
    val country: String,
    val sessionCount: Int,
    val totalDuration: Long,
    val latitude: Double?,
    val longitude: Double?,
    val isTestUser: Boolean
)

// ─── Interfaces ───────────────────────────────────────────────────────────────

interface DeviceApiService {
    /** POST /api/devices/register */
    suspend fun registerDevice(payload: DevicePayload)
}

interface TrackingApiService {
    /** GET /api/tracking/click-link?url=...&notificationId=...&token=... */
    suspend fun trackClick(url: String, notificationId: String, token: String)
}

// ─── Implementations ──────────────────────────────────────────────────────────

private val JSON = "application/json; charset=utf-8".toMediaType()

internal class DeviceApiServiceImpl(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String,
    private val appId: String
) : DeviceApiService {

    override suspend fun registerDevice(payload: DevicePayload) {
        val body = gson.toJson(payload).toRequestBody(JSON)
        val request = Request.Builder()
            .url("$baseUrl/api/devices/register")
            .post(body)
            .addHeader("x-active-workspace", appId)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
        }
    }
}

internal class TrackingApiServiceImpl(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val appId: String
) : TrackingApiService {

    override suspend fun trackClick(url: String, notificationId: String, token: String) {
        val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
        val encodedNotifId = java.net.URLEncoder.encode(notificationId, "UTF-8")
        val encodedToken = java.net.URLEncoder.encode(token, "UTF-8")
        val fullUrl = "$baseUrl/api/tracking/click-link?url=$encodedUrl&notificationId=$encodedNotifId&token=$encodedToken"
        val request = Request.Builder()
            .url(fullUrl)
            .get()
            .addHeader("x-active-workspace", appId)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 302) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
        }
    }
}
