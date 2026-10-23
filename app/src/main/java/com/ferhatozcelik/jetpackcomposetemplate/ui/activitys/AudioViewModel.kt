package com.ferhatozcelik.jetpackcomposetemplate.ui.activitys

import android.app.Application
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

data class Track(val id: Long, val title: String, val artist: String?, val durationMs: Long)

/**
 * Holds the music library and the connection to [AudioPlayerService]. Lives across
 * rotations, so the controller connects once and is released in [onCleared].
 * Everything is Compose state, so the UI reads it directly.
 */
class AudioViewModel(app: Application) : AndroidViewModel(app) {

    /** Null until the first MediaStore query finishes. */
    var tracks by mutableStateOf<List<Track>?>(null)
        private set

    // Mirror of the player, refreshed on every player event.
    var currentId by mutableStateOf<Long?>(null)
        private set
    var title by mutableStateOf<String?>(null)
        private set
    var artist by mutableStateOf<String?>(null)
        private set
    var artwork by mutableStateOf<ImageBitmap?>(null)
        private set
    var showPlay by mutableStateOf(true)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set

    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(
        app, SessionToken(app, ComponentName(app, AudioPlayerService::class.java))
    ).buildAsync()
    private var loadJob: Job? = null
    private var artJob: Job? = null
    private var artBytes: ByteArray? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        controllerFuture.addListener({
            // get() throws if the ViewModel was cleared before the connection finished.
            controller = runCatching { controllerFuture.get() }.getOrNull()?.also {
                it.addListener(listener)
                sync(it)
            }
        }, MoreExecutors.directExecutor())
    }

    /** Re-reads the library. Cheap enough to call every time the app comes to the foreground. */
    fun loadTracks() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            tracks = withContext(Dispatchers.IO) { queryTracks(getApplication<Application>().contentResolver) }
        }
    }

    fun play(trackId: Long) {
        val player = controller ?: return
        val list = tracks ?: return
        val index = list.indexOfFirst { it.id == trackId }
        if (index < 0) return
        // Only hand the whole library to the player when it holds a different one,
        // e.g. on first play or after files were added since.
        if (player.hasPlaylist(list)) {
            player.seekTo(index, 0L)
        } else {
            player.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
        }
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    /** Also restarts after the end of the playlist or a playback error. */
    fun playPause() {
        Util.handlePlayPauseButtonAction(controller)
    }

    /** Restarts the current track if it is past the first few seconds, like most players. */
    fun previous() {
        controller?.seekToPrevious()
    }

    fun next() {
        controller?.seekToNext()
    }

    fun seekTo(fraction: Float) {
        val player = controller ?: return
        if (durationMs <= 0) return
        player.seekTo((fraction * durationMs).toLong())
        positionMs = player.currentPosition
    }

    /** Called by the UI about 30 times a second while playing. The controller extrapolates the position locally. */
    fun refreshPosition() {
        controller?.let { positionMs = it.currentPosition }
    }

    private fun sync(player: Player) {
        currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
        val metadata = player.mediaMetadata
        title = metadata.title?.toString()
        artist = metadata.artist?.toString()
        showPlay = Util.shouldShowPlayButton(player)
        durationMs = player.duration.coerceAtLeast(0L) // C.TIME_UNSET is negative
        positionMs = player.currentPosition
        loadArtwork(metadata.artworkData)
    }

    /** ExoPlayer already extracted the embedded cover; decode it small, off the main thread. */
    private fun loadArtwork(bytes: ByteArray?) {
        if (bytes === artBytes || bytes contentEquals artBytes) return
        artBytes = bytes
        artJob?.cancel()
        if (bytes == null) {
            artwork = null
            return
        }
        artJob = viewModelScope.launch {
            artwork = withContext(Dispatchers.Default) { decodeThumbnail(bytes, ARTWORK_PX) }
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
    }
}

private const val ARTWORK_PX = 256

private fun queryTracks(resolver: ContentResolver): List<Track> {
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
    )
    val tracks = resolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        null
    )?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        ArrayList<Track>(c.count).apply {
            while (c.moveToNext()) {
                add(
                    Track(
                        id = c.getLong(idCol),
                        title = c.getString(titleCol).orEmpty(),
                        artist = c.getString(artistCol)?.takeUnless { it == MediaStore.UNKNOWN_STRING },
                        durationMs = c.getLong(durationCol),
                    )
                )
            }
        }
    } ?: return emptyList()
    // Sorted here rather than in SQL so accents and case sort the way the user's language expects.
    val collator = Collator.getInstance()
    tracks.sortWith { a, b -> collator.compare(a.title, b.title) }
    return tracks
}

private fun Track.toMediaItem() = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build())
    .build()

private fun Player.hasPlaylist(tracks: List<Track>): Boolean =
    mediaItemCount == tracks.size &&
        tracks.indices.all { getMediaItemAt(it).mediaId == tracks[it].id.toString() }

/** Decodes at the smallest power-of-two scale that is still at least [minPx] on each side. */
private fun decodeThumbnail(bytes: ByteArray, minPx: Int): ImageBitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    var sample = 1
    while (options.outWidth / (sample * 2) >= minPx && options.outHeight / (sample * 2) >= minPx) sample *= 2
    options.inJustDecodeBounds = false
    options.inSampleSize = sample
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}
