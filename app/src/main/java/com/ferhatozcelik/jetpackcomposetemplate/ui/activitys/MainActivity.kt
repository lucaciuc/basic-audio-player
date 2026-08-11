package com.ferhatozcelik.jetpackcomposetemplate.ui.activitys

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay

data class AudioFile(val id: Long, val name: String, val extension: String, val uri: Uri)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AudioPlayerRoot()
                }
            }
        }
    }
}

@Composable
fun AudioPlayerRoot() {
    val context = LocalContext.current
    var audioFiles by remember { mutableStateOf(emptyList<AudioFile>()) }
    var hasPermission by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) audioFiles = fetchAudioFiles(context)
    }

    LaunchedEffect(Unit) { launcher.launch(permission) }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Storage permission required.")
        }
        return
    }

    // All player state lives here, passed down as primitives
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var playing by remember { mutableStateOf(false) }

    // Connect once
    LaunchedEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, AudioPlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { controller = future.get() },
            ContextCompat.getMainExecutor(context)
        )
    }

    // Load tracks once
    LaunchedEffect(controller, audioFiles) {
        val ctrl = controller ?: return@LaunchedEffect
        if (audioFiles.isNotEmpty() && ctrl.mediaItemCount == 0) {
            ctrl.setMediaItems(audioFiles.map { file ->
                MediaItem.Builder()
                    .setUri(file.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder().setTitle(file.name).build()
                    )
                    .build()
            })
            ctrl.prepare()
        }
    }

    // Listen to player events — only update index and playing state
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = controller?.currentMediaItemIndex ?: -1
            }
            override fun onIsPlayingChanged(isNowPlaying: Boolean) {
                playing = isNowPlaying
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }

    // UI — the list never recomposes from slider updates
    Column(Modifier.fillMaxSize()) {
        TrackList(
            files = audioFiles,
            currentIndex = currentIndex,
            onTrackClick = { index ->
                controller?.seekTo(index, 0L)
                controller?.play()
            },
            modifier = Modifier.weight(1f)
        )

        controller?.let { ctrl ->
            PlayerBar(
                controller = ctrl,
                isPlaying = playing
            )
        }
    }
}

// ── Track List ─────────────────────────────────────────────────────
// This composable only recomposes when currentIndex changes.
// The slider state is completely isolated in PlayerBar.

@Composable
fun TrackList(
    files: List<AudioFile>,
    currentIndex: Int,
    onTrackClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            items = files,
            key = { it.id }
        ) { file ->
            val index = files.indexOf(file)
            val selected = index == currentIndex

            Surface(
                color = if (selected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTrackClick(index) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = file.extension.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Player Bar ─────────────────────────────────────────────────────
// This composable manages its OWN slider state internally.
// Nothing it does causes the list above to recompose.

@Composable
fun PlayerBar(
    controller: MediaController,
    isPlaying: Boolean
) {
    // Slider state is local to this composable — isolated from the rest
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }

    // Poll position only while playing, at a reasonable rate
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!isDragging) {
                val dur = controller.duration.coerceAtLeast(1L)
                duration = dur
                sliderPosition = controller.currentPosition.toFloat() / dur.toFloat()
            }
            delay(250L)
        }
        // When paused, sync one final time
        if (!isDragging) {
            val dur = controller.duration.coerceAtLeast(1L)
            duration = dur
            sliderPosition = controller.currentPosition.toFloat() / dur.toFloat()
        }
    }

    // Reset slider on track change
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                sliderPosition = 0f
                duration = 0L
            }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            Slider(
                value = sliderPosition.coerceIn(0f, 1f),
                onValueChange = {
                    isDragging = true
                    sliderPosition = it
                },
                onValueChangeFinished = {
                    controller.seekTo((sliderPosition * duration).toLong())
                    isDragging = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    controller.seekToPreviousMediaItem()
                    controller.play()
                }) {
                    Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(28.dp))
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            if (controller.isPlaying) controller.pause()
                            else controller.play()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = {
                    controller.seekToNextMediaItem()
                    controller.play()
                }) {
                    Icon(Icons.Default.SkipNext, "Next", Modifier.size(28.dp))
                }
            }
        }
    }
}

// ── MediaStore Query ───────────────────────────────────────────────

fun fetchAudioFiles(context: Context): List<AudioFile> {
    val list = mutableListOf<AudioFile>()

    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.MIME_TYPE
    )

    context.contentResolver.query(
        collection,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol)
            val ext = cursor.getString(mimeCol).substringAfterLast("/", "unknown")
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            list.add(AudioFile(id, name, ext, uri))
        }
    }
    return list
}