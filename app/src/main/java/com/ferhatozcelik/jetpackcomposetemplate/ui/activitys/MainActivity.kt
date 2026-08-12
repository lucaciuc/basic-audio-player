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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay

@Immutable
data class AudioFile(val id: Long, val name: String, val extension: String, val uri: Uri)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Material 3 Dynamic Theme
            val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val colors = if (dynamicColor) {
                dynamicDarkColorScheme(LocalContext.current)
            } else {
                darkColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFF90CAF9),
                    surface = androidx.compose.ui.graphics.Color(0xFF111111)
                )
            }

            MaterialTheme(colorScheme = colors) {
                Surface(
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AudioPlayerRoot()
                }
            }
        }
    }
}

@Composable
fun AudioPlayerRoot(viewModel: AudioViewModel = viewModel()) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }

    // Phase 1: Observe audio files from ViewModel StateFlow (background-loaded)
    val audioFiles by viewModel.audioFiles.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.loadAudioFiles()
    }

    LaunchedEffect(Unit) { launcher.launch(permission) }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Storage permission required.")
        }
        return
    }

    // Show loading indicator while ViewModel is fetching files
    if (isLoading && audioFiles.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, AudioPlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { controller = future.get() },
            ContextCompat.getMainExecutor(context)
        )
    }

    LaunchedEffect(controller, audioFiles) {
        val ctrl = controller ?: return@LaunchedEffect
        if (audioFiles.isNotEmpty() && ctrl.mediaItemCount == 0) {
            ctrl.setMediaItems(audioFiles.map { file ->
                MediaItem.Builder()
                    .setUri(file.uri)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(file.name).build())
                    .build()
            })
            ctrl.prepare()
        }
    }

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
            PlayerBar(controller = ctrl, isPlaying = playing)
        }
    }
}

// ── Track List ─────────────────────────────────────────────────────
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
        itemsIndexed(
            items = files, 
            key = { _, file -> file.id },
            contentType = { _, _ -> "track" }
        ) { index, file ->
            val selected = index == currentIndex
            TrackListItem(
                file = file,
                isSelected = selected,
                onClick = { onTrackClick(index) }
            )
        }
    }
}

@Composable
fun TrackListItem(
    file: AudioFile,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = file.extension,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

// ── Phase 2: Phase-Deferred Player Bar (Zero Recomposition Slider) ─
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PlayerBar(
    controller: MediaController,
    isPlaying: Boolean
) {
    var rawPosition by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!isDragging) {
                duration = controller.duration.coerceAtLeast(1L).toFloat()
                rawPosition = controller.currentPosition.toFloat()
            }
            delay(250L) // Poll position every 250ms
        }
        if (!isDragging) {
            duration = controller.duration.coerceAtLeast(1L).toFloat()
            rawPosition = controller.currentPosition.toFloat()
        }
    }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                rawPosition = 0f
            }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    // This smooths out the 250ms polling jumps without touching the playlist
    val animatedPositionValue by animateFloatAsState(
        targetValue = rawPosition,
        animationSpec = tween(if (isPlaying && !isDragging) 250 else 0),
        label = "slider_anim"
    )

    // Phase 2: derivedStateOf defers this calculation to the Drawing phase only.
    // Compose skips the Composition phase entirely when only the slider value changes.
    val progress by remember {
        derivedStateOf { (animatedPositionValue / duration).coerceIn(0f, 1f) }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Slider(
                value = progress,
                onValueChange = { percent ->
                    isDragging = true
                    rawPosition = percent * duration
                },
                onValueChangeFinished = {
                    controller.seekTo(rawPosition.toLong())
                    isDragging = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    controller.seekToPreviousMediaItem()
                    controller.play()
                }) {
                    Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(32.dp))
                }

                // Premium Material 3 Animated Play/Pause Button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable {
                            if (controller.isPlaying) controller.pause() else controller.play()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isPlaying,
                        label = "play_pause_anim"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                IconButton(onClick = {
                    controller.seekToNextMediaItem()
                    controller.play()
                }) {
                    Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

// ── MediaStore Query (kept for legacy reference, ViewModel uses its own) ──
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
            val ext = cursor.getString(mimeCol).substringAfterLast("/", "unknown").uppercase()
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            list.add(AudioFile(id, name, ext, uri))
        }
    }
    return list
}