package com.ferhatozcelik.jetpackcomposetemplate.ui.activitys

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class AudioPlayerService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 1. Audio Focus Management
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Phase 8: Gapless Playback - Configure LoadControl to aggressively
        // pre-buffer the next track while the current one is still playing.
        // back_buffer keeps decoded audio in RAM so seeking backwards is instant.
        val loadControl = DefaultLoadControl.Builder()
            .setBackBuffer(
                /* backBufferDurationMs = */ 30_000,    // Keep 30s of played audio in RAM
                /* retainBackBufferFromKeyframe = */ true
            )
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setLoadControl(loadControl)
            .build()

        // Phase 8: Tell ExoPlayer to NOT pause between tracks (gapless)
        player.pauseAtEndOfMediaItems = false

        // Enable Hardware Audio Offload (Battery Saver)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(
                androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                    .build()
            )
            .build()

        // Phase 4: Attach Equalizer and BassBoost to the audio session
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                // Release previous instances to prevent leaks
                releaseAudioEffects()
                if (audioSessionId != 0) {
                    try {
                        equalizer = Equalizer(0, audioSessionId).apply {
                            enabled = true
                            // Apply a pleasant default - users can customize via UI
                        }
                        bassBoost = BassBoost(0, audioSessionId).apply {
                            enabled = true
                            setStrength(200) // Subtle bass enhancement (0-1000)
                        }
                    } catch (_: Exception) {
                        // Some devices don't support audio effects
                    }
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        super.onTaskRemoved(rootIntent)
        player.stop()
        stopSelf()
    }

    override fun onDestroy() {
        releaseAudioEffects()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun releaseAudioEffects() {
        try {
            equalizer?.release()
            equalizer = null
            bassBoost?.release()
            bassBoost = null
        } catch (_: Exception) { }
    }
}
