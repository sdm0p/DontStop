package com.dontstop.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.LocalDate

private val Context.dontStopDataStore by preferencesDataStore("dontstop_settings")

data class AllowanceSettings(val scope: String, val sharedLimit: Long, val perAppLimits: Map<String, Long>, val reminders: Int) {
    fun limitFor(packageName: String): Long = if (scope == "shared") sharedLimit else perAppLimits[packageName] ?: sharedLimit
}

data class NativeConfiguration(
    val apps: Map<String, Boolean>, val time: AllowanceSettings,
    val swipes: AllowanceSettings, val respectDnd: Boolean
) {
    val trackedPackages: Set<String> get() = apps.filterValues { it }.keys
}

data class TrackingSettings(
    val enabled: Boolean, val activeConfig: NativeConfiguration,
    val pendingConfig: NativeConfiguration? = null, val activatesOn: String? = null,
    val assistantBall: String = "auto"
) {
    val trackedPackages get() = activeConfig.trackedPackages

    companion object {
        val APP_ID_TO_PACKAGE = linkedMapOf(
            "instagram" to "com.instagram.android", "youtube" to "com.google.android.youtube",
            "facebook" to "com.facebook.katana"
        )
        val PACKAGE_TO_APP_ID = APP_ID_TO_PACKAGE.entries.associate { (id, pkg) -> pkg to id }
        val SUPPORTED_PACKAGES = APP_ID_TO_PACKAGE.values.toSet()
        val DEFAULT_CONFIG = NativeConfiguration(
            apps = APP_ID_TO_PACKAGE.values.associateWith { it != "com.facebook.katana" },
            time = AllowanceSettings("shared", 120, mapOf(
                "com.instagram.android" to 60, "com.google.android.youtube" to 60, "com.facebook.katana" to 45
            ), 3),
            swipes = AllowanceSettings("shared", 300, mapOf(
                "com.instagram.android" to 150, "com.google.android.youtube" to 150, "com.facebook.katana" to 100
            ), 3), respectDnd = true
        )
    }
}

class SettingsStore(private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("tracking_enabled")
        val initialized = booleanPreferencesKey("configuration_initialized")
        val activeConfig = stringPreferencesKey("active_configuration_v2")
        val pendingConfig = stringPreferencesKey("pending_configuration_v2")
        val activatesOn = stringPreferencesKey("pending_activates_on")
        val overlayX = intPreferencesKey("overlay_x")
        val overlayY = intPreferencesKey("overlay_y")
        val assistantBall = stringPreferencesKey("assistant_ball")
    }

    suspend fun get(): TrackingSettings {
        activatePendingIfDue()
        return context.dontStopDataStore.data.map { p -> TrackingSettings(
            enabled = p[Keys.enabled] ?: false,
            activeConfig = p[Keys.activeConfig]?.let(::decode) ?: TrackingSettings.DEFAULT_CONFIG,
            pendingConfig = p[Keys.pendingConfig]?.let(::decode), activatesOn = p[Keys.activatesOn],
            assistantBall = p[Keys.assistantBall] ?: "auto"
        ) }.first()
    }

    /** Configuration edits apply immediately so limits can be tuned and tested the same day. */
    suspend fun saveConfiguration(config: NativeConfiguration) {
        validate(config)
        context.dontStopDataStore.edit { p ->
            p[Keys.activeConfig] = encode(config)
            p[Keys.initialized] = true
            p.remove(Keys.pendingConfig)
            p.remove(Keys.activatesOn)
        }
    }

    suspend fun setEnabled(enabled: Boolean) = context.dontStopDataStore.edit { it[Keys.enabled] = enabled }
    suspend fun overlayPosition(): Pair<Int, Int> = context.dontStopDataStore.data.map {
        (it[Keys.overlayX] ?: 0) to (it[Keys.overlayY] ?: 200)
    }.first()
    suspend fun setOverlayPosition(x: Int, y: Int) = context.dontStopDataStore.edit { it[Keys.overlayX] = x; it[Keys.overlayY] = y }
    suspend fun setAssistantBall(value: String) = context.dontStopDataStore.edit { it[Keys.assistantBall] = value }

    private suspend fun activatePendingIfDue() {
        context.dontStopDataStore.edit { p ->
            val date = p[Keys.activatesOn] ?: return@edit
            val pending = p[Keys.pendingConfig] ?: return@edit
            if (LocalDate.now() >= LocalDate.parse(date)) {
                p[Keys.activeConfig] = pending; p.remove(Keys.pendingConfig); p.remove(Keys.activatesOn)
            }
        }
    }

    private fun validate(config: NativeConfiguration) {
        require(config.apps.keys == TrackingSettings.SUPPORTED_PACKAGES) { "apps must include all supported apps" }
        require(config.apps.values.any { it }) { "At least one app must be enabled" }
        listOf("time" to config.time, "swipes" to config.swipes).forEach { (metric, allowance) ->
            require(allowance.scope == "shared" || allowance.scope == "perApp") { "$metric scope must be shared or perApp" }
            require(allowance.sharedLimit > 0 && allowance.perAppLimits.values.all { it > 0 }) { "$metric limits must be positive" }
            require(allowance.perAppLimits.keys == TrackingSettings.SUPPORTED_PACKAGES) { "$metric perAppLimits must include every app" }
            val smallest = if (allowance.scope == "shared") allowance.sharedLimit
                else config.trackedPackages.minOf { allowance.perAppLimits.getValue(it) }
            require(allowance.reminders > 0) { "$metric reminders must be positive" }
            if (metric == "swipes") {
                require(allowance.reminders.toLong() <= smallest) { "$metric reminders must create distinct milestones" }
            }
        }
    }

    private fun encode(config: NativeConfiguration): String = JSONObject().apply {
        put("apps", JSONObject(config.apps)); put("time", encodeAllowance(config.time))
        put("swipes", encodeAllowance(config.swipes)); put("respectDnd", config.respectDnd)
    }.toString()
    private fun encodeAllowance(value: AllowanceSettings) = JSONObject().apply {
        put("scope", value.scope); put("sharedLimit", value.sharedLimit)
        put("perAppLimits", JSONObject(value.perAppLimits)); put("reminders", value.reminders)
    }
    private fun decode(raw: String): NativeConfiguration {
        val json = JSONObject(raw); val appsJson = json.getJSONObject("apps")
        val apps = TrackingSettings.SUPPORTED_PACKAGES.associateWith { appsJson.optBoolean(it, false) }
        return NativeConfiguration(apps, decodeAllowance(json.getJSONObject("time")),
            decodeAllowance(json.getJSONObject("swipes")), json.optBoolean("respectDnd", true))
    }
    private fun decodeAllowance(json: JSONObject): AllowanceSettings {
        val limits = json.getJSONObject("perAppLimits")
        return AllowanceSettings(json.getString("scope"), json.getLong("sharedLimit"),
            TrackingSettings.SUPPORTED_PACKAGES.associateWith { limits.getLong(it) }, json.getInt("reminders"))
    }
}
