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

    fun configService(baseUrl: String, appId: String): ConfigApiService =
        ConfigApiServiceImpl(buildClient(), gson, baseUrl.trimEnd('/'), appId)
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

    private fun getFirebaseSenderId(): String? {
        return try {
            val firebaseAppClass = Class.forName("com.google.firebase.FirebaseApp")
            val getInstanceMethod = firebaseAppClass.getMethod("getInstance")
            val firebaseAppInstance = getInstanceMethod.invoke(null)
            val getOptionsMethod = firebaseAppClass.getMethod("getOptions")
            val optionsInstance = getOptionsMethod.invoke(firebaseAppInstance)
            val optionsClass = Class.forName("com.google.firebase.FirebaseOptions")
            val getGcmSenderIdMethod = optionsClass.getMethod("getGcmSenderId")
            getGcmSenderIdMethod.invoke(optionsInstance) as String?
        } catch (e: Exception) {
            null
        }
    }

    private fun getFirebaseProjectId(): String? {
        return try {
            val firebaseAppClass = Class.forName("com.google.firebase.FirebaseApp")
            val getInstanceMethod = firebaseAppClass.getMethod("getInstance")
            val firebaseAppInstance = getInstanceMethod.invoke(null)
            val getOptionsMethod = firebaseAppClass.getMethod("getOptions")
            val optionsInstance = getOptionsMethod.invoke(firebaseAppInstance)
            val optionsClass = Class.forName("com.google.firebase.FirebaseOptions")
            val getProjectIdMethod = optionsClass.getMethod("getProjectId")
            getProjectIdMethod.invoke(optionsInstance) as String?
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun registerDevice(payload: DevicePayload) {
        val body = gson.toJson(payload).toRequestBody(JSON)
        val builder = Request.Builder()
            .url("$baseUrl/api/devices/register")
            .post(body)
            .addHeader("x-active-workspace", appId)

        getFirebaseSenderId()?.let {
            builder.addHeader("x-active-sender-id", it)
        }
        getFirebaseProjectId()?.let {
            builder.addHeader("x-active-project-id", it)
        }

        val request = builder.build()
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

interface ConfigApiService {
    /** GET /api/applications/:appId/fcm-config */
    suspend fun getFcmConfig(): FcmConfigResponse

    /** GET /api/applications/:appId/android-params */
    suspend fun getAndroidParams(packageName: String): AndroidParamsResponse
}

data class FcmConfigResponse(
    val appId: String,
    val name: String,
    val senderId: String?,
    val senderIds: List<String>?
)

data class AndroidParamsResponse(
    val project_number: String?,
    val project_id: String?,
    val app_id_fcm: String?,
    val api_key: String?,
    val package_name: String?
)

internal class ConfigApiServiceImpl(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String,
    private val appId: String
) : ConfigApiService {

    override suspend fun getFcmConfig(): FcmConfigResponse {
        val request = Request.Builder()
            .url("$baseUrl/api/applications/$appId/fcm-config")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            val bodyString = response.body?.string() ?: throw Exception("Empty response body")
            return gson.fromJson(bodyString, FcmConfigResponse::class.java)
        }
    }

    override suspend fun getAndroidParams(packageName: String): AndroidParamsResponse {
        val request = Request.Builder()
            .url("$baseUrl/api/applications/$appId/android-params?packageName=${java.net.URLEncoder.encode(packageName, "UTF-8")}")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            val bodyString = response.body?.string() ?: throw Exception("Empty response body")
            return gson.fromJson(bodyString, AndroidParamsResponse::class.java)
        }
    }
}
