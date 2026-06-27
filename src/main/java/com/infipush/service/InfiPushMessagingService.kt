package com.infipush.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.infipush.InfiPush
import com.infipush.receiver.NotificationClickActivity
import org.json.JSONArray
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Firebase Messaging Service for the InfiPush SDK.
 *
 * This service is auto-registered via the SDK's AndroidManifest merge,
 * so the host app does NOT need to add any service declaration.
 *
 * Handles two FCM events:
 *  1. [onNewToken]     — sends the new FCM token to the InfiPush backend
 *  2. [onMessageReceived] — builds and displays the push notification
 */
class InfiPushMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "InfiPushMessaging"

        // Keys expected in FCM data payload from InfiPush backend
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_IMAGE = "image"
        const val KEY_LAUNCH_URL = "launchUrl"
        const val KEY_NOTIF_ID = "notificationId"
        const val KEY_ACTION_BUTTONS = "actionButtons"

        // Intent extras forwarded to NotificationClickReceiver
        const val EXTRA_LAUNCH_URL = "extra_launch_url"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val EXTRA_TOKEN = "extra_token"
    }

    // ── Token refresh ──────────────────────────────────────────────────────────

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        // Delegate to singleton which will POST /api/devices/register
        if (InfiPush.isInitialized) {
            InfiPush.onNewToken(token)
        }
    }

    // ── Message received ───────────────────────────────────────────────────────

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received from: ${message.from}")

        // PushSaaS always uses the *data* payload (works in background & foreground)
        val data = message.data
        val title = data[KEY_TITLE] ?: message.notification?.title ?: return
        val body = data[KEY_BODY] ?: message.notification?.body ?: return

        showNotification(title, body, data)
    }

    // ── Notification Builder ───────────────────────────────────────────────────

    private fun showNotification(
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        ensureChannel()

        val config = InfiPush.config
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val launchUrl = data[KEY_LAUNCH_URL]
        val notificationId = data[KEY_NOTIF_ID] ?: ""
        val token = data["deviceToken"] ?: ""
        
        val bigPictureUrl = data["bigPicture"] ?: data["image"]
        val largeIconUrl = data["largeIcon"]
        val accentColorHex = data["accentColor"] ?: data["ledColor"]
        val actionButtonsJson = data["actionButtons"]
        val ledColorHex = data["ledColor"]
        val visibilityStr = data["visibility"]
        val groupKey = data["groupKey"]

        val notifIntId = notificationId.hashCode()

        // PendingIntent → routes through NotificationClickActivity for tracking
        val clickIntent = Intent(this, NotificationClickActivity::class.java).apply {
            putExtra(EXTRA_LAUNCH_URL, launchUrl ?: "")
            putExtra(EXTRA_NOTIF_ID, notificationId)
            putExtra(EXTRA_TOKEN, token)
        }
        val clickPending = PendingIntent.getActivity(
            this,
            notifIntId,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        var colorInt = config.accentColor
        if (!accentColorHex.isNullOrBlank()) {
            try {
                colorInt = Color.parseColor(accentColorHex)
            } catch (e: Exception) {
                Log.e(TAG, "Invalid accent color: $accentColorHex", e)
            }
        }

        var ledColorInt = Color.GREEN
        if (!ledColorHex.isNullOrBlank()) {
            try {
                ledColorInt = Color.parseColor(ledColorHex)
            } catch (e: Exception) {
                Log.e(TAG, "Invalid LED color: $ledColorHex", e)
            }
        }

        val iconRes = if (config.smallIconRes == 0) {
            applicationInfo.icon
        } else {
            config.smallIconRes
        }

        val builder = NotificationCompat.Builder(this, config.channelId)
            .setSmallIcon(iconRes)
            .setColor(colorInt)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(clickPending)
            .setLights(ledColorInt, 1000, 1000)

        // Visibility
        if (!visibilityStr.isNullOrBlank()) {
            val visibilityInt = when (visibilityStr.lowercase()) {
                "public" -> NotificationCompat.VISIBILITY_PUBLIC
                "private" -> NotificationCompat.VISIBILITY_PRIVATE
                "secret" -> NotificationCompat.VISIBILITY_SECRET
                else -> NotificationCompat.VISIBILITY_PUBLIC
            }
            builder.setVisibility(visibilityInt)
        }

        // Group Key
        if (!groupKey.isNullOrBlank()) {
            builder.setGroup(groupKey)
        }

        // Detail / BigTextStyle (default if no big picture)
        var style: NotificationCompat.Style = NotificationCompat.BigTextStyle().bigText(body)

        // Large Icon (logo)
        if (!largeIconUrl.isNullOrBlank()) {
            val largeIconBitmap = getBitmapFromUrl(largeIconUrl)
            if (largeIconBitmap != null) {
                builder.setLargeIcon(largeIconBitmap)
            }
        }

        // Big Picture Image
        if (!bigPictureUrl.isNullOrBlank()) {
            val bigPictureBitmap = getBitmapFromUrl(bigPictureUrl)
            if (bigPictureBitmap != null) {
                style = NotificationCompat.BigPictureStyle()
                    .bigPicture(bigPictureBitmap)
                    .setSummaryText(body)
            }
        }

        builder.setStyle(style)

        // Action Buttons
        if (!actionButtonsJson.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(actionButtonsJson)
                for (i in 0 until jsonArray.length()) {
                    val btnObj = jsonArray.getJSONObject(i)
                    val btnId = btnObj.optString("id", "")
                    val btnTitle = btnObj.optString("title", "")
                    if (btnId.isNotBlank() && btnTitle.isNotBlank()) {
                        val btnIntent = Intent(this, NotificationClickActivity::class.java).apply {
                            putExtra(EXTRA_LAUNCH_URL, launchUrl ?: "")
                            putExtra(EXTRA_NOTIF_ID, notificationId)
                            putExtra(EXTRA_TOKEN, token)
                            putExtra("extra_action_id", btnId)
                        }
                        val btnPending = PendingIntent.getActivity(
                            this,
                            (notifIntId + btnId.hashCode()),
                            btnIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        builder.addAction(0, btnTitle, btnPending)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse action buttons", e)
            }
        }

        notifManager.notify(notifIntId, builder.build())
        Log.i(TAG, "Notification displayed: $title")
    }

    private fun getBitmapFromUrl(imageUrl: String?): Bitmap? {
        if (imageUrl.isNullOrBlank()) return null
        return try {
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            BitmapFactory.decodeStream(connection.inputStream)
        } catch (e: Throwable) {
            Log.e(TAG, "Error downloading image from $imageUrl", e)
            null
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val config = InfiPush.config
            val channel = NotificationChannel(
                config.channelId,
                config.channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Push notifications from ${applicationInfo.loadLabel(packageManager)}"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
