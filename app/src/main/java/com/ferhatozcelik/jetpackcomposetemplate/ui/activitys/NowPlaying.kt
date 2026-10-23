package com.ferhatozcelik.jetpackcomposetemplate.ui.activitys

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ferhatozcelik.jetpackcomposetemplate.R
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Docked player in the style of the Pixel media controls. */
@Composable
fun NowPlayingCard(vm: AudioViewModel, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val playing = !vm.showPlay

    // About 30 position updates a second (the delay plus the wait for the next frame, on 60,
    // 90 and 120 Hz screens), which also move the wave. That is smooth for this slow motion and
    // draws a quarter of the frames a 120 Hz screen would. Each read is cheap (the controller
    // extrapolates the position locally), and withFrameNanos waits while the app is in the
    // background, so the loop stops there.
    if (playing) {
        LaunchedEffect(vm) {
            while (true) {
                delay(TICK_MS)
                withFrameNanos { vm.refreshPosition() }
            }
        }
    }
    Column(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .background(colors.surfaceContainerHigh, RoundedCornerShape(28.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(vm)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    vm.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    vm.artist ?: stringResource(R.string.unknown_artist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PlayPauseButton(showPlay = vm.showPlay, onClick = vm::playPause)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::previous) {
                Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.previous))
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                WavySeekBar(
                    progress = { if (vm.durationMs > 0) vm.positionMs.toFloat() / vm.durationMs else 0f },
                    phase = { vm.positionMs * WAVES_PER_MS % 1f },
                    playing = playing,
                    onSeek = vm::seekTo,
                )
                TimeLabels(vm)
            }
            IconButton(onClick = vm::next) {
                Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.next))
            }
        }
    }
}

/** Its own scope, so only these two labels recompose, once per second. */
@Composable
private fun TimeLabels(vm: AudioViewModel) {
    val elapsedSeconds by remember(vm) { derivedStateOf { vm.positionMs / 1000 } }
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(formatTime(elapsedSeconds * 1000), style = style, color = color)
        Text(formatTime(vm.durationMs), style = style, color = color)
    }
}

@Composable
private fun Artwork(vm: AudioViewModel) {
    val shape = RoundedCornerShape(16.dp)
    val art = vm.artwork
    if (art != null) {
        Image(art, null, Modifier.size(56.dp).clip(shape), contentScale = ContentScale.Crop)
    } else {
        Icon(
            painterResource(R.drawable.ic_music_note),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, shape)
                .padding(16.dp),
        )
    }
}

/** A circle while paused that morphs into a rounded square while playing, as on Pixel. */
@Composable
private fun PlayPauseButton(showPlay: Boolean, onClick: () -> Unit) {
    val cornerPercent by animateIntAsState(if (showPlay) 50 else 30, label = "playPauseShape")
    val colors = MaterialTheme.colorScheme
    Icon(
        painterResource(if (showPlay) R.drawable.ic_play else R.drawable.ic_pause),
        contentDescription = stringResource(if (showPlay) R.string.play else R.string.pause),
        tint = colors.onPrimary,
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(cornerPercent))
            .background(colors.primary)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
    )
}

/**
 * The Pixel "squiggly" seek bar: the played part is a wave that flows while music plays
 * and flattens when paused. [progress] and [phase] (in wavelengths) are only read while
 * drawing, so position updates redraw this canvas without recomposing anything.
 */
@Composable
private fun WavySeekBar(
    progress: () -> Float,
    phase: () -> Float,
    playing: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outlineVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val seek by rememberUpdatedState(onSeek)
    val description = stringResource(R.string.seek)

    val amplitude = animateFloatAsState(if (playing) 1f else 0f, tween(400), label = "waveAmplitude")
    val drag = remember { mutableFloatStateOf(-1f) } // Fraction while the finger is down
    // Accessibility reads whole percents, so it is not refreshed on every position update.
    val percent = remember(progress) { derivedStateOf { (progress().coerceIn(0f, 1f) * 100).roundToInt() } }
    val wave = remember { Path() }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(percent.value / 100f, 0f..1f)
                setProgress { seek(it); true }
            }
            .pointerInput(rtl) {
                val inset = TRACK_INSET.toPx()
                fun fractionAt(x: Float): Float {
                    val f = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                    return if (rtl) 1f - f else f
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    drag.floatValue = fractionAt(down.position.x)
                    val released = horizontalDrag(down.id) { change ->
                        drag.floatValue = fractionAt(change.position.x)
                        change.consume()
                    }
                    if (released) seek(drag.floatValue)
                    drag.floatValue = -1f
                }
            }
    ) {
        val p = drag.floatValue.takeIf { it >= 0f } ?: progress().coerceIn(0f, 1f)
        val stroke = 4.dp.toPx()
        val gap = 6.dp.toPx()
        val start = TRACK_INSET.toPx()
        val end = size.width - start
        val x = start + (end - start) * p
        val cy = size.height / 2

        scale(if (rtl) -1f else 1f, 1f) {
            if (x + gap < end) {
                drawLine(inactive, Offset(x + gap, cy), Offset(end, cy), stroke, StrokeCap.Round)
            }
            if (x - gap > start) {
                val amp = 3.dp.toPx() * amplitude.value
                val k = TWO_PI / 32.dp.toPx() // One wave every 32dp
                val shift = phase() * TWO_PI
                val step = 2.dp.toPx()
                val waveEnd = x - gap
                wave.rewind()
                wave.moveTo(start, cy - amp * sin(shift))
                var px = start
                while (px < waveEnd) {
                    px = min(px + step, waveEnd)
                    wave.lineTo(px, cy + amp * sin((px - start) * k - shift))
                }
                drawPath(wave, active, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            // Vertical handle, as on the Material 3 Expressive slider
            drawLine(active, Offset(x, cy - 10.dp.toPx()), Offset(x, cy + 10.dp.toPx()), stroke, StrokeCap.Round)
        }
    }
}

private val TRACK_INSET = 6.dp
private const val TWO_PI = (2 * PI).toFloat()
private const val TICK_MS = 25L
private const val WAVES_PER_MS = 0.6f / 1000f

/** 0:42, 3:07, 1:02:03 */
fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total / 60 % 60
    val s = total % 60
    val ss = if (s < 10) "0$s" else "$s"
    return when {
        h > 0 -> "$h:${if (m < 10) "0$m" else "$m"}:$ss"
        else -> "$m:$ss"
    }
}
