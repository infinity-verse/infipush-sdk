package com.infipush.model

/**
 * Configuration object passed to [com.infipush.InfiPush.init].
 *
 * @property baseUrl        Full base URL of your backend (e.g. "http://10.0.2.2:8000" for emulator)
 * @property appVersion     Current app version string registered with the device (e.g. "1.0.0")
 * @property defaultTags    Key-value tags always attached to the device record (e.g. user_type, country)
 * @property channelId      Android notification channel ID — create it yourself for custom sound/vibration
 * @property channelName    Human-readable channel name shown in system settings
 * @property smallIconRes   Drawable resource for notification small icon (must be a monochrome drawable). Defaults to 0 (application launcher icon).
 * @property accentColor    Notification accent / LED color as ARGB Int
 * @property debug          Enable verbose SDK logging
 */
data class InfiPushConfig(
    val baseUrl: String = "https://api.infipush.infinityinfotech.co.in",
    val appId: String = "",
    val appVersion: String = "1.0.0",
    val defaultTags: Map<String, String> = emptyMap(),
    val channelId: String = "infipush_default",
    val channelName: String = "InfiPush Notifications",
    val smallIconRes: Int = 0,
    val accentColor: Int = 0xFF0F766E.toInt(),   // Emerald brand colour
    val debug: Boolean = false
)
