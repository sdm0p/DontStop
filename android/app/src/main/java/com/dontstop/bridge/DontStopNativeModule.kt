package com.dontstop.bridge

import android.app.AppOpsManager
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import com.dontstop.storage.DontStopDatabase
import com.dontstop.storage.AllowanceSettings
import com.dontstop.storage.NativeConfiguration
import com.dontstop.storage.SettingsStore
import com.dontstop.storage.TrackingSettings
import com.dontstop.tracking.DontStopAccessibilityService
import com.dontstop.tracking.TrackingEngine
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DontStopNativeModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = SettingsStore(reactContext)
    private val database = DontStopDatabase(reactContext)

    override fun getName() = "DontStopNative"

    @ReactMethod
    fun getStatus(promise: Promise): Unit {
        scope.launch {
        runCatching {
            val settings = store.get()
            Arguments.createMap().apply {
                putBoolean("trackingEnabled", settings.enabled)
                putBoolean("accessibilityGranted", accessibilityEnabled())
                putBoolean("usageAccessGranted", usageAccessGranted())
                putBoolean("overlayGranted", Settings.canDrawOverlays(reactContext))
                putBoolean("notificationsGranted", notificationsGranted())
                putString("detectionStatus", if (accessibilityEnabled()) "available" else "permission_required")
            }
        }.fold(promise::resolve) { promise.reject("STATUS_ERROR", it.message, it) }
        }
    }

    @ReactMethod
    fun getSettings(promise: Promise): Unit {
        scope.launch {
        runCatching { settingsMap(store.get()) }.fold(promise::resolve) { promise.reject("SETTINGS_ERROR", it.message, it) }
        }
    }

    @ReactMethod
    fun getAssistantBalls(promise: Promise): Unit {
        scope.launch {
            runCatching {
                Arguments.createArray().also { result ->
                    result.pushString("auto")
                    reactContext.assets.list("assistant_balls").orEmpty()
                        .filter { it.endsWith(".png", true) || it.endsWith(".jpg", true) || it.endsWith(".webp", true) }
                        .sorted()
                        .forEach(result::pushString)
                }
            }.fold(promise::resolve) { promise.reject("ASSISTANT_BALLS_ERROR", it.message, it) }
        }
    }

    @ReactMethod
    fun setAssistantBall(value: String, promise: Promise): Unit {
        scope.launch {
            runCatching {
                val valid = value == "auto" || reactContext.assets.list("assistant_balls").orEmpty().any { it == value }
                require(valid) { "Unknown assistant-ball template" }
                store.setAssistantBall(value)
                TrackingEngine.get(reactContext).onAssistantBallChanged()
            }.fold({ promise.resolve(null) }) { promise.reject("ASSISTANT_BALL_ERROR", it.message, it) }
        }
    }

    @ReactMethod
    fun updateSettings(input: ReadableMap, promise: Promise): Unit {
        scope.launch {
        runCatching {
            val config = parseConfiguration(input)
            store.saveConfiguration(config)
            TrackingEngine.get(reactContext).onConfigurationChanged().join()
            settingsMap(store.get())
        }.fold(promise::resolve) { promise.reject("INVALID_SETTINGS", it.message, it) }
        }
    }

    @ReactMethod
    fun setTrackingEnabled(enabled: Boolean, promise: Promise): Unit {
        scope.launch {
        runCatching {
            store.setEnabled(enabled)
            if (!enabled) TrackingEngine.get(reactContext).stop()
            enabled
        }.fold(promise::resolve) { promise.reject("TRACKING_ERROR", it.message, it) }
        }
    }

    @ReactMethod
    fun getUsageSnapshot(promise: Promise): Unit {
        scope.launch {
        runCatching {
            val today = Arguments.createArray()
            database.today().forEach { row -> today.pushMap(Arguments.createMap().apply {
                putString("packageName", row.packageName); putDouble("minutes", row.minutes); putDouble("swipes", row.swipes.toDouble())
            }) }
            val history = Arguments.createArray()
            database.history().forEach { (day, rows) -> history.pushMap(Arguments.createMap().apply {
                putString("day", day)
                putArray("apps", Arguments.createArray().also { apps -> rows.forEach { row -> apps.pushMap(Arguments.createMap().apply {
                    putString("packageName", row.packageName); putDouble("minutes", row.minutes); putDouble("swipes", row.swipes.toDouble())
                }) } })
            }) }
            Arguments.createMap().apply { putString("schemaVersion", "1"); putArray("today", today); putArray("history", history) }
        }.fold(promise::resolve) { promise.reject("SNAPSHOT_ERROR", it.message, it) }
        }
    }

    @ReactMethod
    fun openPermissionSettings(permission: String, promise: Promise): Unit {
        runCatching {
            if (permission == "notifications" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !notificationsGranted()) {
                val activity = reactContext.currentActivity ?: error("The app must be open to request notification permission")
                activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
            } else {
                openSettingsIntent(permission)
            }
        }.fold({ promise.resolve(null) }) { promise.reject("SETTINGS_OPEN_ERROR", it.message, it) }
    }

    @ReactMethod fun startOverlay(promise: Promise): Unit { promise.resolve(Settings.canDrawOverlays(reactContext)) }
    @ReactMethod fun stopOverlay(promise: Promise): Unit { TrackingEngine.get(reactContext).stop(); promise.resolve(null) }
    @ReactMethod fun addListener(eventName: String): Unit = Unit
    @ReactMethod fun removeListeners(count: Int): Unit = Unit

    @ReactMethod
    fun resetToday(metric: String, promise: Promise): Unit {
        scope.launch {
            runCatching { TrackingEngine.get(reactContext).resetToday(metric) }
                .fold({ promise.resolve(null) }) { promise.reject("RESET_ERROR", it.message, it) }
        }
    }

    @ReactMethod
    fun deleteHistory(promise: Promise): Unit {
        scope.launch {
        runCatching { database.clearHistory() }.fold({ promise.resolve(null) }) { promise.reject("DELETE_ERROR", it.message, it) }
        }
    }

    private fun accessibilityEnabled(): Boolean {
        val expected = ComponentName(reactContext, DontStopAccessibilityService::class.java)
        val manager = reactContext.getSystemService(AccessibilityManager::class.java)
        val enabledByManager = manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val service = info.resolveInfo.serviceInfo
                service.packageName == expected.packageName && service.name == expected.className
            }
        if (enabledByManager) return true

        val enabled = Settings.Secure.getString(reactContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled?.split(':')?.any { value ->
            val parsed = ComponentName.unflattenFromString(value)
            parsed == expected || value.equals(expected.flattenToString(), ignoreCase = true) ||
                value.equals(expected.flattenToShortString(), ignoreCase = true)
        } == true
    }

    private fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            reactContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openSettingsIntent(permission: String) {
        val packageUri = Uri.parse("package:${reactContext.packageName}")
        val primary = when (permission) {
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "usage" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(packageUri)
            "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(packageUri)
            "dnd" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, reactContext.packageName)
            else -> error("Unknown permission: $permission")
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val fallback = when (permission) {
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "usage" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(packageUri)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        reactContext.startActivity(if (primary.resolveActivity(reactContext.packageManager) != null) primary else fallback)
    }

    private fun usageAccessGranted(): Boolean {
        val mode = reactContext.getSystemService(AppOpsManager::class.java)
            .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), reactContext.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun settingsMap(value: TrackingSettings) = Arguments.createMap().apply {
        putBoolean("enabled", value.enabled)
        putMap("activeConfig", configurationMap(value.activeConfig))
        value.pendingConfig?.let { putMap("pendingConfig", configurationMap(it)) }
        value.activatesOn?.let { putString("activatesOn", it) }
        putString("assistantBall", value.assistantBall)
    }

    private fun configurationMap(value: NativeConfiguration) = Arguments.createMap().apply {
        putMap("apps", Arguments.createMap().also { apps -> TrackingSettings.APP_ID_TO_PACKAGE.forEach { (id, pkg) ->
            apps.putBoolean(id, value.apps[pkg] == true)
        } })
        putMap("time", allowanceMap(value.time))
        putMap("swipes", allowanceMap(value.swipes))
        putBoolean("respectDnd", value.respectDnd)
    }

    private fun allowanceMap(value: AllowanceSettings) = Arguments.createMap().apply {
        putString("scope", value.scope); putDouble("sharedLimit", value.sharedLimit.toDouble())
        putMap("perAppLimits", Arguments.createMap().also { limits -> TrackingSettings.APP_ID_TO_PACKAGE.forEach { (id, pkg) ->
            limits.putDouble(id, (value.perAppLimits[pkg] ?: 0).toDouble())
        } })
        putInt("reminders", value.reminders)
    }

    private fun parseConfiguration(input: ReadableMap): NativeConfiguration {
        val appsInput = input.getMap("apps") ?: error("apps is required")
        val apps = TrackingSettings.APP_ID_TO_PACKAGE.mapValues { (id, _) -> appsInput.getBoolean(id) }
            .mapKeys { (id, _) -> TrackingSettings.APP_ID_TO_PACKAGE.getValue(id) }
        return NativeConfiguration(
            apps = apps,
            time = parseAllowance(input.getMap("time") ?: error("time is required")),
            swipes = parseAllowance(input.getMap("swipes") ?: error("swipes is required")),
            respectDnd = input.getBoolean("respectDnd")
        )
    }

    private fun parseAllowance(input: ReadableMap): AllowanceSettings {
        val limitsInput = input.getMap("perAppLimits") ?: error("perAppLimits is required")
        val limits = TrackingSettings.APP_ID_TO_PACKAGE.mapValues { (id, _) -> limitsInput.getDouble(id).toLong() }
            .mapKeys { (id, _) -> TrackingSettings.APP_ID_TO_PACKAGE.getValue(id) }
        return AllowanceSettings(input.getString("scope") ?: error("scope is required"),
            input.getDouble("sharedLimit").toLong(), limits, input.getInt("reminders"))
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 7401
    }
}
