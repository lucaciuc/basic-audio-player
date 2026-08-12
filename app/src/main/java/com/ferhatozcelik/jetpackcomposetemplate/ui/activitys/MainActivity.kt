package com.ferhatozcelik.jetpackcomposetemplate.ui.activitys

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ferhatozcelik.jetpackcomposetemplate.R
import com.ferhatozcelik.jetpackcomposetemplate.databinding.ActivityMainBinding
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.Slider

data class AudioFile(val id: Long, val name: String, val extension: String, val uri: Uri)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var controller: MediaController? = null
    private lateinit var adapter: TrackAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var userDragging = false

    private val progressUpdater = object : Runnable {
        override fun run() {
            val ctrl = controller ?: return
            if (ctrl.isPlaying && !userDragging) {
                val duration = ctrl.duration.coerceAtLeast(1L)
                val position = ctrl.currentPosition
                binding.slider.value = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            }
            handler.postDelayed(this, 500)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadAndConnect()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup RecyclerView
        adapter = TrackAdapter { index -> playTrack(index) }
        binding.recyclerTracks.layoutManager = LinearLayoutManager(this)
        binding.recyclerTracks.setHasFixedSize(true)
        binding.recyclerTracks.adapter = adapter

        // Setup buttons
        binding.btnPlayPause.setOnClickListener {
            controller?.let { c ->
                if (c.isPlaying) c.pause() else c.play()
            }
        }
        binding.btnPrevious.setOnClickListener {
            controller?.seekToPreviousMediaItem()
            controller?.play()
        }
        binding.btnNext.setOnClickListener {
            controller?.seekToNextMediaItem()
            controller?.play()
        }

        // Setup Material Slider
        binding.slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                userDragging = true
            }
            override fun onStopTrackingTouch(slider: Slider) {
                val ctrl = controller ?: return
                val duration = ctrl.duration.coerceAtLeast(1L)
                ctrl.seekTo((slider.value * duration).toLong())
                userDragging = false
            }
        })

        // Request permission
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            loadAndConnect()
        } else {
            permissionLauncher.launch(perm)
        }
    }

    private fun loadAndConnect() {
        val files = fetchAudioFiles(this)
        adapter.submitList(files)

        val token = SessionToken(this, ComponentName(this, AudioPlayerService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            controller = future.get()
            setupController(files)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupController(files: List<AudioFile>) {
        val ctrl = controller ?: return

        if (ctrl.mediaItemCount == 0 && files.isNotEmpty()) {
            ctrl.setMediaItems(files.map { file ->
                MediaItem.Builder()
                    .setUri(file.uri)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(file.name).build())
                    .build()
            })
            ctrl.prepare()
        }

        ctrl.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = ctrl.currentMediaItemIndex
                adapter.setActiveIndex(index)
                binding.textNowPlaying.text = mediaItem?.mediaMetadata?.title ?: "Unknown"
                binding.slider.value = 0f
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.btnPlayPause.setIconResource(
                    if (isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
            }
        })

        handler.post(progressUpdater)
    }

    private fun playTrack(index: Int) {
        controller?.seekTo(index, 0L)
        controller?.play()
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressUpdater)
        super.onDestroy()
    }
}

// ── ListAdapter with DiffUtil ──────────────────────────────────────
// Uses DiffUtil for efficient list updates. Track highlight changes
// use payload-based partial rebind (only the background color changes,
// the text is never re-set).

class TrackAdapter(
    private val onClick: (Int) -> Unit
) : ListAdapter<AudioFile, TrackAdapter.VH>(DIFF) {

    private var activeIndex: Int = -1

    companion object {
        private const val PAYLOAD_HIGHLIGHT = "highlight"

        val DIFF = object : DiffUtil.ItemCallback<AudioFile>() {
            override fun areItemsTheSame(old: AudioFile, new: AudioFile) = old.id == new.id
            override fun areContentsTheSame(old: AudioFile, new: AudioFile) = old == new
        }
    }

    fun setActiveIndex(index: Int) {
        val old = activeIndex
        activeIndex = index
        if (old >= 0) notifyItemChanged(old, PAYLOAD_HIGHLIGHT)
        if (index >= 0) notifyItemChanged(index, PAYLOAD_HIGHLIGHT)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = getItem(position)
        holder.name.text = file.name
        holder.ext.text = file.extension.uppercase()
        holder.itemView.setOnClickListener { onClick(position) }
        applyHighlight(holder, position)
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_HIGHLIGHT)) {
            // Partial rebind — only update the background, don't touch text
            applyHighlight(holder, position)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun applyHighlight(holder: VH, position: Int) {
        val isActive = position == activeIndex
        val ctx = holder.itemView.context
        if (isActive) {
            val color = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimaryContainer)
            holder.itemView.setBackgroundColor(color)
            val textColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnPrimaryContainer)
            holder.name.setTextColor(textColor)
        } else {
            holder.itemView.setBackgroundColor(0x00000000) // transparent
            val textColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface)
            holder.name.setTextColor(textColor)
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.textTrackName)
        val ext: TextView = view.findViewById(R.id.textTrackExt)
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