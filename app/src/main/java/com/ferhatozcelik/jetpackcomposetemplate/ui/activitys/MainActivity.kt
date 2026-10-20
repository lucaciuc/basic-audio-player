package com.ferhatozcelik.jetpackcomposetemplate.ui.activitys

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ferhatozcelik.jetpackcomposetemplate.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelTheme { PlayerScreen() }
        }
    }
}

private val AudioPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun PlayerScreen(vm: AudioViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    fun isGranted() = context.checkSelfPermission(AudioPermission) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(isGranted()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    // After "Don't allow" twice Android stops showing the dialog; only Settings can help then.
    var blocked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        blocked = !it && activity?.shouldShowRequestPermissionRationale(AudioPermission) == false
        if (it) vm.loadTracks()
    }

    // On every start: catches a permission granted in Settings and files added meanwhile.
    LifecycleStartEffect(vm) {
        granted = isGranted()
        if (granted) {
            vm.loadTracks()
        } else if (!asked) {
            asked = true
            launcher.launch(AudioPermission)
        }
        onStopOrDispose {}
    }

    // Surface also sets the default text and icon color for everything inside.
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))) {
            val hasMedia = vm.currentId != null
            if (granted) {
                TrackList(
                    tracks = vm.tracks,
                    currentId = vm.currentId,
                    onPlay = vm::play,
                    // Without the player card the list scrolls behind the navigation bar.
                    bottomPadding = if (hasMedia) 8.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
                    modifier = Modifier.weight(1f),
                )
            } else {
                PermissionRequest(
                    blocked = blocked,
                    onAllow = { launcher.launch(AudioPermission) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            if (hasMedia) NowPlayingCard(vm)
        }
    }
}

@Composable
private fun PermissionRequest(
    blocked: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_music_note),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                .padding(20.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = if (blocked) onOpenSettings else onAllow) {
            Text(stringResource(if (blocked) R.string.permission_settings else R.string.permission_allow))
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>?,
    currentId: Long?,
    onPlay: (Long) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "header", contentType = "header") {
            Column(Modifier.padding(start = 8.dp, top = 48.dp, bottom = 24.dp)) {
                Text(stringResource(R.string.library_title), style = MaterialTheme.typography.displaySmall)
                if (tracks != null) {
                    Text(
                        if (tracks.isEmpty()) stringResource(R.string.no_music)
                        else pluralStringResource(R.plurals.song_count, tracks.size, tracks.size),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (tracks != null) {
            itemsIndexed(tracks, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
                TrackRow(
                    track = track,
                    shape = segmentShape(index, tracks.size),
                    selected = track.id == currentId,
                    onClick = { onPlay(track.id) },
                )
            }
        }
    }
}

// One rounded group, like Android 16 settings lists: big outer corners, small inner ones.
private val Single = RoundedCornerShape(24.dp)
private val First = RoundedCornerShape(24.dp, 24.dp, 4.dp, 4.dp)
private val Middle = RoundedCornerShape(4.dp)
private val Last = RoundedCornerShape(4.dp, 4.dp, 24.dp, 24.dp)

private fun segmentShape(index: Int, count: Int) = when {
    count == 1 -> Single
    index == 0 -> First
    index == count - 1 -> Last
    else -> Middle
}

@Composable
private fun TrackRow(
    track: Track,
    shape: RoundedCornerShape,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (selected) R.drawable.ic_graphic_eq else R.drawable.ic_music_note),
            contentDescription = null,
            tint = if (selected) colors.onPrimary else colors.onSecondaryContainer,
            modifier = Modifier
                .size(40.dp)
                .background(if (selected) colors.primary else colors.secondaryContainer, RoundedCornerShape(12.dp))
                .padding(8.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist ?: stringResource(R.string.unknown_artist),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatTime(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        )
    }
}
