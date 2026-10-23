package com.ferhatozcelik.jetpackcomposetemplate.ui.activitys

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Owns the player. Media3 provides the media notification, lock screen and Bluetooth
 * controls, and keeps playback going after the activity is gone. When the app is
 * swiped away, the default [onTaskRemoved] stops the service unless music is playing.
 */
class AudioPlayerService : MediaSessionService() {
    private var session: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // ExoPlayer reads the device's decoder list once per process, right before the
        // first song. Doing it now, off the main thread, takes that wait out of the first tap.
        Thread {
            for (mimeType in COMMON_AUDIO_TYPES) {
                MediaCodecUtil.warmDecoderInfoCache(mimeType, /* secure = */ false, /* tunneling = */ false)
            }
        }.start()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // Pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL) // Keep decoding with the screen off
            .setLoadControl(
                // Files on the phone load far faster than they play, so start (and resume after
                // a seek) once a quarter second is buffered instead of ExoPlayer's full second.
                DefaultLoadControl.Builder()
                    .setBufferDurationsMsForLocalPlayback(
                        DefaultLoadControl.DEFAULT_MIN_BUFFER_FOR_LOCAL_PLAYBACK_MS,
                        DefaultLoadControl.DEFAULT_MAX_BUFFER_FOR_LOCAL_PLAYBACK_MS,
                        /* bufferForPlaybackMs = */ 250,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_FOR_LOCAL_PLAYBACK_MS,
                    )
                    .build()
            )
            .build()
        // Hardware audio offload stays off (the default). With it, ExoPlayer stops running and
        // waits, with no timeout, for the audio chip to ask for more data; when that request is
        // lost, as can happen around quick repeated seeks, playback stays silent until the next
        // seek. Every seek also throws away and recreates its 50-second audio buffer.

        // Tapping the notification brings the existing task back, like the launcher does.
        val openApp = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        session = MediaSession.Builder(this, player)
            .setSessionActivity(PendingIntent.getActivity(this, 0, openApp, PendingIntent.FLAG_IMMUTABLE))
            // Controllers, the system media controls included, extrapolate the position
            // themselves. Without this they are woken with a new state every 3 s while playing.
            .setPeriodicPositionUpdateEnabled(false)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

private val COMMON_AUDIO_TYPES = arrayOf(
    MimeTypes.AUDIO_MPEG,
    MimeTypes.AUDIO_AAC,
    MimeTypes.AUDIO_FLAC,
    MimeTypes.AUDIO_OPUS,
    MimeTypes.AUDIO_VORBIS,
)
