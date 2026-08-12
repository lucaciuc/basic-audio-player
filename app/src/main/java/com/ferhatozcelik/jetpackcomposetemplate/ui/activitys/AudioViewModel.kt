package com.ferhatozcelik.jetpackcomposetemplate.ui.activitys

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel that loads audio files on a background thread (Dispatchers.IO),
 * completely eliminating any UI-thread blocking during the MediaStore query.
 * The result is exposed as a StateFlow so Compose can observe it reactively.
 */
class AudioViewModel(application: Application) : AndroidViewModel(application) {

    private val _audioFiles = MutableStateFlow<List<AudioFile>>(emptyList())
    val audioFiles: StateFlow<List<AudioFile>> = _audioFiles.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadAudioFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            val files = withContext(Dispatchers.IO) {
                fetchAudioFilesFromMediaStore()
            }
            _audioFiles.value = files
            _isLoading.value = false
        }
    }

    /**
     * Performs the heavy MediaStore cursor query entirely on Dispatchers.IO.
     * Uses a pre-sized ArrayList for zero resizing overhead.
     */
    private fun fetchAudioFilesFromMediaStore(): List<AudioFile> {
        val context = getApplication<Application>()
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

        val cursor = context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        ) ?: return emptyList()

        // Pre-allocate list to cursor count — zero resizing, zero GC pressure
        val list = ArrayList<AudioFile>(cursor.count)

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol)
                val ext = c.getString(mimeCol).substringAfterLast("/", "unknown").uppercase()
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                list.add(AudioFile(id, name, ext, uri))
            }
        }

        return list
    }
}
