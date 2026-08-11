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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
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
                    LocalAudioPlayerApp()
                }
            }
        }
    }
}

@Composable
fun LocalAudioPlayerApp() {
    val context = LocalContext.current
    var audioFiles by remember { mutableStateOf<List<AudioFile>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(false) }

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasPermission = isGranted
            if (isGranted) {
                audioFiles = fetchAudioFiles(context)
            }
        }
    )

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionToRequest)
    }

    if (hasPermission) {
        AudioPlayerScreen(audioFiles = audioFiles)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Storage permission is required to find audio files.")
        }
    }
}

@Composable
fun AudioPlayerScreen(audioFiles: List<AudioFile>) {
    val context = LocalContext.current
    var mediaController by remember { mutableStateOf<MediaController?>(null) }

    var currentSongIndex by remember { mutableStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }

    // Connect to the background MediaSessionService
    LaunchedEffect(Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, AudioPlayerService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener(
            { mediaController = controllerFuture.get() },
            ContextCompat.getMainExecutor(context)
        )
    }

    // Load files into the controller
    LaunchedEffect(mediaController, audioFiles) {
        val controller = mediaController ?: return@LaunchedEffect

        if (audioFiles.isNotEmpty() && controller.mediaItemCount == 0) {
            val mediaItems = audioFiles.map { file ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(file.name)
                    .setSubtitle("Type: ${file.extension}")
                    .build()

                MediaItem.Builder()
                    .setUri(file.uri)
                    .setMediaMetadata(metadata)
                    .build()
            }

            controller.setMediaItems(mediaItems)
            controller.prepare()
        }
    }

    // React to player state changes
    DisposableEffect(mediaController) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentSongIndex = mediaController?.currentMediaItemIndex ?: -1
                currentPosition = 0L // Reset position on track change
            }

            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
            }
        }

        mediaController?.addListener(listener)

        onDispose {
            mediaController?.removeListener(listener)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Track List
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = audioFiles,
                key = { _, file -> file.id }
            ) { index, file ->
                val isSelected = index == currentSongIndex
                
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.02f else 1f,
                    animationSpec = tween(300),
                    label = "scale_anim"
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(scale)
                        .clickable {
                            mediaController?.seekTo(index, 0L)
                            mediaController?.play()
                        }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = file.name, style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Type: ${file.extension}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Playback Controls
        if (mediaController != null) {
            PlaybackControls(
                mediaController = mediaController!!,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                totalDuration = totalDuration,
                onPositionChange = { newPos -> currentPosition = newPos },
                onDurationChange = { newDur -> totalDuration = newDur }
            )
        }
    }
}

@Composable
fun PlaybackControls(
    mediaController: MediaController,
    isPlaying: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    onPositionChange: (Long) -> Unit,
    onDurationChange: (Long) -> Unit
) {
    // Update the progress slider
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            onPositionChange(mediaController.currentPosition)
            onDurationChange(mediaController.duration.coerceAtLeast(0L))
            delay(1000L)
        }
    }

    // Smooth animated slider
    val animatedPosition by animateFloatAsState(
        targetValue = currentPosition.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "progress_anim"
    )

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Slider(
                value = if (totalDuration > 0) animatedPosition / totalDuration.toFloat() else 0f,
                onValueChange = { newPercent ->
                    val newPosition = (newPercent * totalDuration).toLong()
                    mediaController.seekTo(newPosition)
                    onPositionChange(newPosition)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    mediaController.seekToPreviousMediaItem()
                    mediaController.play()
                }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                }

                FloatingActionButton(onClick = {
                    if (mediaController.isPlaying) {
                        mediaController.pause()
                    } else {
                        mediaController.play()
                    }
                }) {
                    Crossfade(targetState = isPlaying, label = "play_pause") { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play"
                        )
                    }
                }

                IconButton(onClick = {
                    mediaController.seekToNextMediaItem()
                    mediaController.play()
                }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
            }
        }
    }
}

// MediaStore Query
fun fetchAudioFiles(context: Context): List<AudioFile> {
    val fileList = mutableListOf<AudioFile>()

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

    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

    context.contentResolver.query(
        collection,
        projection,
        selection,
        null,
        "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn)
            val mimeType = cursor.getString(mimeTypeColumn)

            val extension = mimeType.substringAfterLast("/", "unknown")
            val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

            fileList.add(AudioFile(id, name, extension, contentUri))
        }
    }

    return fileList
}