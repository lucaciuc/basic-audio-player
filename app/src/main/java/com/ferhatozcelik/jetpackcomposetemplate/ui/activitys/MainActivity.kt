package com.ferhatozcelik.jetpackcomposetemplate.ui.activitys

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ferhatozcelik.jetpackcomposetemplate.databinding.ActivityMainBinding

data class AudioFile(val id: Long, val name: String, val extension: String, val uri: Uri)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var controller: MediaController? = null
    private lateinit var adapter: TrackAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var userDragging = false

    // Periodically update the seekbar while playing
    private val progressUpdater = object : Runnable {
        override fun run() {
            val ctrl = controller ?: return
            if (ctrl.isPlaying && !userDragging) {
                val duration = ctrl.duration.coerceAtLeast(1L)
                val position = ctrl.currentPosition
                binding.seekBar.progress = ((position * 1000) / duration).toInt()
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

        // Setup seekbar
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(sb: SeekBar?) { userDragging = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                val ctrl = controller ?: return
                val duration = ctrl.duration.coerceAtLeast(1L)
                ctrl.seekTo((sb!!.progress.toLong() * duration) / 1000)
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

        // Load media items
        if (ctrl.mediaItemCount == 0 && files.isNotEmpty()) {
            ctrl.setMediaItems(files.map { file ->
                MediaItem.Builder()
                    .setUri(file.uri)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(file.name).build())
                    .build()
            })
            ctrl.prepare()
        }

        // Listen for state changes
        ctrl.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = ctrl.currentMediaItemIndex
                adapter.setActiveIndex(index)
                binding.textNowPlaying.text = mediaItem?.mediaMetadata?.title ?: "Unknown"
                binding.seekBar.progress = 0
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.btnPlayPause.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
            }
        })

        // Start progress updates
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

// ── RecyclerView Adapter ───────────────────────────────────────────
// Ultra-lightweight: each row is just two TextViews. No cards, no
// animations, no recomposition. Highlighting is a simple background
// color change on bind.

class TrackAdapter(
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<TrackAdapter.VH>() {

    private var tracks: List<AudioFile> = emptyList()
    private var activeIndex: Int = -1

    fun submitList(newTracks: List<AudioFile>) {
        tracks = newTracks
        notifyDataSetChanged()
    }

    fun setActiveIndex(index: Int) {
        val old = activeIndex
        activeIndex = index
        if (old >= 0) notifyItemChanged(old)
        if (index >= 0) notifyItemChanged(index)
    }

    override fun getItemCount() = tracks.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(com.ferhatozcelik.jetpackcomposetemplate.R.layout.item_track, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = tracks[position]
        holder.name.text = file.name
        holder.ext.text = file.extension.uppercase()

        val isActive = position == activeIndex
        holder.name.setTextColor(if (isActive) Color.WHITE else Color.parseColor("#DDDDDD"))
        holder.itemView.setBackgroundColor(
            if (isActive) Color.parseColor("#1E3A5F") else Color.TRANSPARENT
        )

        holder.itemView.setOnClickListener { onClick(position) }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(com.ferhatozcelik.jetpackcomposetemplate.R.id.textTrackName)
        val ext: TextView = view.findViewById(com.ferhatozcelik.jetpackcomposetemplate.R.id.textTrackExt)
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