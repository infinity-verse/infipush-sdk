package com.infipush

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.infipush.model.InfiPushConfig
import com.infipush.network.ApiClient
import com.infipush.network.DevicePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * InfiPush — Main entry-point singleton for the InfiPush Android SDK.
 *
 * Usage:
 * ```kotlin
 * // In Application.onCreate():
 * InfiPush.init(context, "your-app-id")
 * ```
 *
 * After [init] the SDK will:
 *  - Persist the configuration across restarts
 *  - Register the FCM token with the backend automatically on first launch and on token refresh
 *  - Display push notifications sent from the dashboard with click-tracking support
 */
object InfiPush {

    private const val TAG = "InfiPush"
    private const val PREFS_NAME = "infipush_prefs"
    private const val KEY_DEVICE_TOKEN = "device_fcm_token"
    private const val KEY_REGISTERED = "device_registered"
    private const val KEY_SESSION_COUNT = "session_count"
    private const val KEY_TOTAL_DURATION = "total_duration"
    private const val KEY_IS_TEST_USER = "is_test_user"

    private var foregroundActivitiesCount = 0
    private var sessionStartTime: Long = 0
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null

    internal lateinit var config: InfiPushConfig
        private set

    internal lateinit var appContext: Context
        private set

    internal val isInitialized: Boolean
        get() = this::appContext.isInitialized

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Initialise the SDK with simple configuration. Call this once inside [android.app.Application.onCreate].
     *
     * @param context Application context
     * @param appId   The InfiPush App ID
     */
    @JvmStatic
    fun init(context: Context, appId: String) {
        val appVersion = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
        val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val defaultTags = mapOf(
            "app" to "sample_android",
            "environment" to if (isDebug) "debug" else "production"
        )
        val config = InfiPushConfig(
            appId = appId,
            appVersion = appVersion,
            defaultTags = defaultTags,
            channelId = "infipush_alerts",
            channelName = "InfiPush Alerts",
            debug = isDebug
        )
        init(context, config)
    }

    /**
     * Initialise the SDK. Call this once inside [android.app.Application.onCreate].
     *
     * @param context Application context
     * @param config  SDK configuration (base URL, app version, tags …)
     */
    @JvmStatic
    fun init(context: Context, config: InfiPushConfig) {
        appContext = context.applicationContext
        InfiPush.config = config
        
        // Register Activity Lifecycle Callbacks to track session count and total active duration
        val app = appContext as? android.app.Application
        app?.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                if (foregroundActivitiesCount == 0) {
                    sessionStartTime = System.currentTimeMillis()
                    
                    // Increment session count
                    val currentSessions = prefs().getInt(KEY_SESSION_COUNT, 0) + 1
                    prefs().edit().putInt(KEY_SESSION_COUNT, currentSessions).apply()
                    Log.d(TAG, "App entered foreground. Session #$currentSessions started.")
                    
                    // Trigger device registration to sync session count
                    registerDevice()
                }
                foregroundActivitiesCount++
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                foregroundActivitiesCount--
                if (foregroundActivitiesCount == 0) {
                    val elapsedSeconds = (System.currentTimeMillis() - sessionStartTime) / 1000
                    val totalDuration = prefs().getLong(KEY_TOTAL_DURATION, 0L) + elapsedSeconds
                    prefs().edit().putLong(KEY_TOTAL_DURATION, totalDuration).apply()
                    Log.d(TAG, "App entered background. Session duration: $elapsedSeconds s. Total duration: $totalDuration s.")
                    
                    // Trigger device registration to sync final duration
                    registerDevice()
                }
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })

        Log.i(TAG, "InfiPush SDK initialised — server: ${config.baseUrl}")
    }

    // ──────────────────────────────────────────────────────────────
    //  Token Management
    // ──────────────────────────────────────────────────────────────

    /**
     * Called by [com.infipush.service.InfiPushMessagingService] whenever
     * Firebase generates or refreshes the FCM registration token.
     */
    fun onNewToken(token: String) {
        Log.d(TAG, "FCM token received: $token")
        val appWiseToken = if (token.contains("|")) token else "${token}|${appContext.packageName}"
        prefs().edit().putString(KEY_DEVICE_TOKEN, appWiseToken).apply()
        registerDeviceWithServer(appWiseToken, isRefresh = true)
    }

    /**
     * Manually trigger device registration.  
     * Useful if you want to attach additional tags at a specific point in your app.
     */
    @JvmStatic
    fun registerDevice(extraTags: Map<String, String> = emptyMap()) {
        val token = prefs().getString(KEY_DEVICE_TOKEN, null) ?: return
        registerDeviceWithServer(token, extraTags = extraTags)
    }

    /**
     * Attach or update profile tags for this device.
     * Tags are merged server-side and used for segment targeting.
     */
    @JvmStatic
    fun setTags(tags: Map<String, String>) {
        registerDevice(extraTags = tags)
    }

    @JvmStatic
    fun setLocation(latitude: Double, longitude: Double) {
        currentLatitude = latitude
        currentLongitude = longitude
        registerDevice()
    }

    @JvmStatic
    fun setTestUser(isTest: Boolean) {
        prefs().edit().putBoolean(KEY_IS_TEST_USER, isTest).apply()
        registerDevice()
    }

    @JvmStatic
    fun setSimulatedCountry(countryCode: String?) {
        if (countryCode != null) {
            prefs().edit().putString("simulated_country_code", countryCode).apply()
        } else {
            prefs().edit().remove("simulated_country_code").apply()
        }
        registerDevice()
    }

    // ──────────────────────────────────────────────────────────────
    //  Internal Helpers
    // ──────────────────────────────────────────────────────────────

    private fun registerDeviceWithServer(
        token: String,
        isRefresh: Boolean = true,
        extraTags: Map<String, String> = emptyMap()
    ) {
        scope.launch {
            try {
                val mergedTags = buildMap<String, String> {
                    putAll(config.defaultTags)
                    putAll(extraTags)
                }

                val language = java.util.Locale.getDefault().language ?: "en"
                val country = prefs().getString("simulated_country_code", null)
                    ?: java.util.Locale.getDefault().country.takeIf { it.isNotEmpty() }
                    ?: "Unknown"
                val sessionCount = prefs().getInt(KEY_SESSION_COUNT, 1)
                val totalDuration = prefs().getLong(KEY_TOTAL_DURATION, 0L)
                val isTestUser = prefs().getBoolean(KEY_IS_TEST_USER, false)

                val payload = DevicePayload(
                    token = token,
                    os = "android",
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    appVersion = config.appVersion,
                    tags = mergedTags,
                    language = language,
                    country = country,
                    sessionCount = sessionCount,
                    totalDuration = totalDuration,
                    latitude = currentLatitude,
                    longitude = currentLongitude,
                    isTestUser = isTestUser
                )

                ApiClient.deviceService(config.baseUrl, config.appId).registerDevice(payload)
                prefs().edit().putBoolean(KEY_REGISTERED, true).apply()
                Log.i(TAG, "Device registered successfully (refresh=$isRefresh)")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(appContext, "✅ Device Registered to Backend!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Device registration failed: ${e.message}", e)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(appContext, "❌ API Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun prefs(): SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
