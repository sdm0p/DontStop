package com.dontstop.tracking

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.SystemClock
import com.dontstop.storage.TrackingSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UsageReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val usage = context.getSystemService(UsageStatsManager::class.java)
                val end = System.currentTimeMillis()
                val events = usage.queryEvents(end - 10 * 60_000, end)
                val event = UsageEvents.Event()
                var foreground: String? = null
                var sawTransition = false
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    when (event.eventType) {
                        UsageEvents.Event.MOVE_TO_FOREGROUND -> { foreground = event.packageName; sawTransition = true }
                        UsageEvents.Event.MOVE_TO_BACKGROUND -> { if (foreground == event.packageName) foreground = null; sawTransition = true }
                    }
                }
                val engine = TrackingEngine.get(context)
                if (sawTransition) {
                    engine.onForegroundChanged(foreground?.takeIf { it in TrackingSettings.SUPPORTED_PACKAGES }).join()
                } else {
                    engine.reconcile().join()
                }
            } finally {
                pending.finish()
            }
        }
    }
}

internal fun scheduleReconciliation(context: Context, delayMs: Long = 3 * 60_000L) {
    val intent = Intent(context, UsageReconcileReceiver::class.java)
    val pending = PendingIntent.getBroadcast(context, 1701, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    context.getSystemService(AlarmManager::class.java).set(
        AlarmManager.ELAPSED_REALTIME_WAKEUP,
        SystemClock.elapsedRealtime() + delayMs.coerceIn(5_000L, 3 * 60_000L),
        pending
    )
}
