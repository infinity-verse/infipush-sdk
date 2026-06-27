package com.infipush.receiver

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.infipush.InfiPush
import com.infipush.network.ApiClient
import com.infipush.service.InfiPushMessagingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Transparent Activity that handles notification clicks and action buttons.
 * Resolves Android 12+ Notification Trampoline restrictions by launching
 * activities directly from an Activity context rather than a BroadcastReceiver.
 */
class NotificationClickActivity : Activity() {

    companion object {
        const val TAG = "NotifClickActivity"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launchUrl = intent.getStringExtra(InfiPushMessagingService.EXTRA_LAUNCH_URL) ?: ""
        val notifId = intent.getStringExtra(InfiPushMessagingService.EXTRA_NOTIF_ID) ?: ""
        val tokenFromIntent = intent.getStringExtra(InfiPushMessagingService.EXTRA_TOKEN) ?: ""

        Log.d(TAG, "Notification clicked — id=$notifId url=$launchUrl")

        // Try to extract the final direct target URL from the tracking URL if present
        var targetUrl = launchUrl
        if (launchUrl.startsWith("http") && launchUrl.contains("/api/tracking/click-link")) {
            try {
                val parsedUri = Uri.parse(launchUrl)
                val redirectUrl = parsedUri.getQueryParameter("url")
                if (!redirectUrl.isNullOrBlank()) {
                    targetUrl = redirectUrl
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing target URL from tracking URL: ${e.message}")
            }
        }

        // 1. Track click asynchronously (fire-and-forget)
        scope.launch {
            try {
                val token = if (tokenFromIntent.isNotBlank()) tokenFromIntent else {
                    getSharedPreferences("infipush_prefs", Context.MODE_PRIVATE)
                        .getString("device_fcm_token", "") ?: ""
                }

                if (notifId.isNotBlank() && token.isNotBlank()) {
                    ApiClient
                        .trackingService(InfiPush.config.baseUrl, InfiPush.config.appId)
                        .trackClick(targetUrl, notifId, token)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Click tracking failed: ${e.message}")
            }
        }

        // 2. Open launch URL directly if present, otherwise bring host app to the foreground
        if (targetUrl.isNotBlank()) {
            val uri = Uri.parse(targetUrl)
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                startActivity(browserIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch target intent: ${e.message}", e)
                bringAppToForeground(this, targetUrl)
            }
        } else {
            bringAppToForeground(this, "")
        }

        // 3. Dismiss this transparent activity immediately
        finish()
    }

    private fun bringAppToForeground(context: Context, url: String) {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("launchUrl", url)
                putExtra("launch_url", url)
            }
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }
    }
}
