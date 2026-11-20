package com.dontstop.tracking

import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import kotlin.random.Random

/**
 * Clips are read from Android assets so each reminder level can have its own
 * visible folder: assets/audio/{gentle,intermediate,savage,continuous}/.
 */
enum class RoastStage { GENTLE, INTERMEDIATE, SAVAGE, CONTINUOUS }

class AudioEnforcer(private val context: Context, private val onFinished: (() -> Unit)? = null) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private var player: MediaPlayer? = null
    private var lastClipPath: String? = null
    private var continuous = false
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs).setOnAudioFocusChangeListener {
            when (it) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { stopLocal(); onFinished?.invoke() }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(.3f, .3f)
                AudioManager.AUDIOFOCUS_GAIN -> player?.setVolume(1f, 1f)
            }
        }.build()

    fun play(stage: RoastStage, repeat: Boolean, respectSilent: Boolean) {
        runCatching {
            context.startForegroundService(Intent(context, ReminderAudioService::class.java)
                .putExtra("stage", stage.name).putExtra("repeat", repeat).putExtra("respectSilent", respectSilent))
        }.onFailure { Log.e(TAG, "Reminder service could not start", it) }
    }

    fun playLocal(stage: RoastStage, repeat: Boolean, respectSilent: Boolean) {
        if (repeat && continuous && player != null) return
        if (respectSilent && (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
                notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL)) {
            Log.i(TAG, "Reminder suppressed by silent/DND setting")
            onFinished?.invoke(); return
        }
        stopLocal()
        continuous = repeat
        playNext(if (repeat) RoastStage.CONTINUOUS else stage)
    }

    private fun playNext(stage: RoastStage) {
        val clips = discover(stage)
        if (clips.isEmpty()) {
            Log.w(TAG, "No MP3 clips found in ${folderFor(stage)}")
            continuous = false
            audio.abandonAudioFocusRequest(focus)
            onFinished?.invoke()
            return
        }
        val eligible = if (clips.size > 1) clips.filterNot { it == lastClipPath } else clips
        val chosen = eligible[Random.nextInt(eligible.size)]
        lastClipPath = chosen
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "Audio focus denied for $stage")
            continuous = false
            onFinished?.invoke()
            return
        }
        val created = runCatching {
            context.assets.openFd(chosen).use { descriptor ->
                MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                    prepare()
                }
            }
        }.getOrElse { error ->
            Log.e(TAG, "MediaPlayer could not load asset=$chosen for $stage", error)
            audio.abandonAudioFocusRequest(focus)
            onFinished?.invoke()
            return
        }
        player = created.apply {
            setVolume(if (continuous) 1f else .75f, if (continuous) 1f else .75f)
            setOnCompletionListener { completed ->
                completed.release(); player = null
                if (continuous) playNext(stage) else { audio.abandonAudioFocusRequest(focus); onFinished?.invoke() }
            }
            setOnErrorListener { failed, _, _ ->
                failed.release(); player = null; continuous = false; audio.abandonAudioFocusRequest(focus); onFinished?.invoke(); true
            }
            start()
            Log.i(TAG, "Started $stage clip asset=$chosen")
        }
    }

    private fun discover(stage: RoastStage): List<String> {
        val folder = folderFor(stage)
        return context.assets.list(folder).orEmpty()
            .filter { it.endsWith(".mp3", ignoreCase = true) }
            .map { "$folder/$it" }
    }

    private fun folderFor(stage: RoastStage) = "audio/${stage.name.lowercase()}"

    fun stop() {
        context.stopService(Intent(context, ReminderAudioService::class.java))
    }

    fun stopLocal() {
        continuous = false
        player?.runCatching { stop() }
        player?.release(); player = null
        audio.abandonAudioFocusRequest(focus)
    }

    companion object { private const val TAG = "DontStopAudio" }
}
