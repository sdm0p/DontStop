package com.dontstop.tracking

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.IBinder

/** Exists only while a reminder is playing; grants background audio eligibility. */
class ReminderAudioService : Service() {
    private lateinit var enforcer: AudioEnforcer
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("reminder_audio", "DONTSTOP reminders", NotificationManager.IMPORTANCE_LOW))
        startForeground(1702, Notification.Builder(this, "reminder_audio")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("DONTSTOP")
            .setContentText("Yaad dila raha hai…")
            .setOngoing(true).build())
        enforcer = AudioEnforcer(this) { stopSelf() }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stage = intent?.getStringExtra("stage")?.let { runCatching { RoastStage.valueOf(it) }.getOrNull() }
        if (stage == null) stopSelf() else enforcer.playLocal(stage, intent.getBooleanExtra("repeat", false), intent.getBooleanExtra("respectSilent", true))
        return START_NOT_STICKY
    }
    override fun onDestroy() { enforcer.stopLocal(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
