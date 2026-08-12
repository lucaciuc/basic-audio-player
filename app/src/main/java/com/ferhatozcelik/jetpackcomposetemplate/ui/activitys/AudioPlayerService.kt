package com.ferhatozcelik.jetpackcomposetemplate.ui.activitys

import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class AudioPlayerService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 1. Audio Focus Management
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // 2. Hardware Audio Offload (Battery Saver)
        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableAudioOffload(true)

        // 3. Enable constant bitrate seeking for FLAC files without seek tables
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        // 4. Tuned buffer: start playback faster after seeking
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,  // min buffer
                50_000,  // max buffer
                1_500,   // buffer for playback (lower = faster resume after seek)
                3_000    // buffer after rebuffer
            )
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(this, extractorsFactory)

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true) // true = handle audio focus automatically
            .setWakeMode(C.WAKE_MODE_LOCAL) // Keeps CPU awake for local file playback when screen is off
            .build()

        // 5. Fast Scrubbing Mode (smooth seekbar dragging)
        player.setScrubbingModeEnabled(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            player.experimentalSetOffloadSchedulingEnabled(true) // Maximizes DSP offload efficiency
        }

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
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
