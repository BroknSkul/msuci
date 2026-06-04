package com.musicplayer.app.data.local.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getDownloadDir(): File {
        val dir = File(context.getExternalFilesDir(null), "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getWallpaperDir(): File {
        val dir = File(context.filesDir, "wallpapers")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
