package com.dontstop.tracking

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import com.dontstop.storage.DailyUsage
import com.dontstop.storage.DontStopDatabase
import com.dontstop.storage.SettingsStore
import com.dontstop.storage.TrackingSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil

class TrackingEngine private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val database = DontStopDatabase(context)
    private val settingsStore = SettingsStore(context)
    private val audio = AudioEnforcer(context)
    private val overlay = OverlayController(context, scope)
    private var foregroundPackage: String? = null
    private var sessionStartedElapsed = 0L
    private var lastScrollElapsed = 0L

    fun onForegroundChanged(packageName: String?) = scope.launch {
        mutex.withLock {
            if (packageName != null && packageName == foregroundPackage) return@withLock
            flushSession()
            val settings = settingsStore.get()
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            val tracked = packageName?.takeIf {
                settings.enabled && it in settings.trackedPackages && !keyguard.isKeyguardLocked
            }
            foregroundPackage = tracked
            sessionStartedElapsed = if (tracked != null) SystemClock.elapsedRealtime() else 0L
            if (tracked == null) {
                audio.stop(); overlay.hide()
            } else {
                evaluate(settings, tracked)
                scheduleReconciliation(context, reconciliationDelayMs(settings, tracked))
            }
        }
    }

    fun onPotentialShortVideoScroll(packageName: String, isShortVideoSurface: Boolean) = scope.launch {
        val now = SystemClock.elapsedRealtime()
        mutex.withLock {
            if (!isShortVideoSurface || foregroundPackage != packageName) return@withLock
            // A single Reel/Short transition produces a burst of scroll events for about
            // 400-500 ms on real devices. Treat the whole animation as one user swipe.
            if (now - lastScrollElapsed < 650) return@withLock
            lastScrollElapsed = now
            database.add(packageName, swipes = 1)
            evaluate(settingsStore.get(), packageName)
        }
    }

    fun reconcile() = scope.launch {
        mutex.withLock {
            val active = foregroundPackage ?: return@withLock
            flushSession()
            sessionStartedElapsed = SystemClock.elapsedRealtime()
            val settings = settingsStore.get()
            evaluate(settings, active)
            scheduleReconciliation(context, reconciliationDelayMs(settings, active))
        }
    }

    fun onConfigurationChanged() = scope.launch {
        mutex.withLock {
            flushSession()
            val settings = settingsStore.get()
            val active = foregroundPackage?.takeIf {
                settings.enabled && it in settings.trackedPackages
            }
            foregroundPackage = active
            sessionStartedElapsed = if (active != null) SystemClock.elapsedRealtime() else 0L
            audio.stop()
            if (active == null) {
                overlay.hide()
            } else {
                evaluate(settings, active)
                scheduleReconciliation(context, reconciliationDelayMs(settings, active))
            }
        }
    }

    fun onAssistantBallChanged() = overlay.refreshTemplate()

    suspend fun resetToday(metric: String) {
        mutex.withLock {
            flushSession()
            database.resetToday(metric)
            sessionStartedElapsed = if (foregroundPackage != null) SystemClock.elapsedRealtime() else 0L
            audio.stop()
            foregroundPackage?.let { evaluate(settingsStore.get(), it) }
        }
    }

    private fun reconciliationDelayMs(settings: TrackingSettings, activePackage: String): Long {
        val time = settings.activeConfig.time
        val limitMs = time.limitFor(activePackage) * 60_000L
        return (limitMs / time.reminders).coerceIn(5_000L, 3 * 60_000L)
    }

    private fun flushSession() {
        val active = foregroundPackage ?: return
        if (sessionStartedElapsed > 0) database.add(active, activeMs = SystemClock.elapsedRealtime() - sessionStartedElapsed)
        sessionStartedElapsed = 0
    }

    private fun currentRows(): List<DailyUsage> {
        val active = foregroundPackage
        val liveMs = if (active != null && sessionStartedElapsed > 0) SystemClock.elapsedRealtime() - sessionStartedElapsed else 0
        return database.today().map { if (it.packageName == active) it.copy(activeMs = it.activeMs + liveMs) else it }
    }

    private fun evaluate(settings: TrackingSettings, activePackage: String) {
        val rows = currentRows()
        val config = settings.activeConfig
        val timeRows = if (config.time.scope == "shared") rows else rows.filter { it.packageName == activePackage }
        val swipeRows = if (config.swipes.scope == "shared") rows else rows.filter { it.packageName == activePackage }
        val time = timeRows.sumOf { it.activeMs }
        val swipes = swipeRows.sumOf { it.swipes }
        val timeLimit = config.time.limitFor(activePackage) * 60_000
        val swipeLimit = config.swipes.limitFor(activePackage)
        val exceeded = time >= timeLimit || swipes >= swipeLimit
        val timeScope = "${if (config.time.scope == "shared") "shared" else activePackage}:$timeLimit:${config.time.reminders}"
        val swipeScope = "${if (config.swipes.scope == "shared") "shared" else activePackage}:$swipeLimit:${config.swipes.reminders}"
        val timeMilestone = (time / (timeLimit.toDouble() / config.time.reminders)).toInt().coerceAtMost(config.time.reminders)
        val swipeMilestone = (swipes / (swipeLimit.toDouble() / config.swipes.reminders)).toInt().coerceAtMost(config.swipes.reminders)
        val timeRatio = timeMilestone.toDouble() / config.time.reminders
        val swipeRatio = swipeMilestone.toDouble() / config.swipes.reminders
        if (exceeded) {
            // The continuous reminder must start only once when a limit is crossed.
            // Accessibility emits many scroll/window events while the user remains in
            // the app; retriggering here made clips appear at random intervals.
            val timeLimitCrossed = time >= timeLimit &&
                !database.milestoneDelivered(timeScope, "time", config.time.reminders)
            val swipeLimitCrossed = swipes >= swipeLimit &&
                !database.milestoneDelivered(swipeScope, "swipes", config.swipes.reminders)
            if (timeLimitCrossed || swipeLimitCrossed) {
                audio.play(RoastStage.SAVAGE, repeat = true, config.respectDnd)
            }
        } else {
            // Claim each metric independently. A delivered time milestone must not
            // hide a newly crossed swipe milestone (or vice versa).
            val newTime = timeMilestone > 0 &&
                !database.milestoneDelivered(timeScope, "time", timeMilestone)
            val newSwipes = swipeMilestone > 0 &&
                !database.milestoneDelivered(swipeScope, "swipes", swipeMilestone)
            if (newTime) for (prior in 1 until timeMilestone) database.milestoneDelivered(timeScope, "time", prior)
            if (newSwipes) for (prior in 1 until swipeMilestone) database.milestoneDelivered(swipeScope, "swipes", prior)
            if (newTime || newSwipes) {
                val ratio = when {
                    newTime && newSwipes -> maxOf(timeRatio, swipeRatio)
                    newTime -> timeRatio
                    else -> swipeRatio
                }
                val stage = when { ratio <= 1.0 / 3 -> RoastStage.GENTLE; ratio <= 2.0 / 3 -> RoastStage.INTERMEDIATE; else -> RoastStage.SAVAGE }
                android.util.Log.i("DontStopAudio", "Milestone time=$time/$timeLimit swipes=$swipes/$swipeLimit newTime=$newTime newSwipes=$newSwipes stage=$stage")
                audio.play(stage, repeat = false, config.respectDnd)
            }
        }
        // The bubble is always personal to the app in front of the user. Limits may
        // still be shared in Settings, but Instagram's message never reports YouTube's usage.
        val appRow = rows.firstOrNull { it.packageName == activePackage }
        val appMinutes = ceil((appRow?.activeMs ?: 0L) / 60_000.0).toLong()
        val appSwipes = appRow?.swipes ?: 0L
        val (contentName, appName) = when (activePackage) {
            "com.instagram.android" -> "reels" to "Instagram"
            "com.google.android.youtube" -> "shorts" to "YouTube"
            "com.facebook.katana" -> "reels" to "Facebook"
            else -> "scrolls" to "this app"
        }
        val detail = "${appSwipes} $contentName ho gaye, beta.\nPadhai kab karega?\n\n$appName par aaj $appMinutes minute"
        overlay.show(detail); overlay.update(detail)
    }

    fun stop() = scope.launch { mutex.withLock { flushSession(); foregroundPackage = null; audio.stop(); overlay.hide() } }

    companion object {
        @Volatile private var instance: TrackingEngine? = null
        fun get(context: Context): TrackingEngine = instance ?: synchronized(this) {
            instance ?: TrackingEngine(context.applicationContext).also { instance = it }
        }
    }
}

