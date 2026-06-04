package com.musicplayer.app.data.local.storage

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: StorageManager
) {
    fun saveWallpaper(uri: Uri): String? {
        val wallpaperDir = storageManager.getWallpaperDir()
        val file = File(wallpaperDir, "custom_wallpaper.jpg")
        
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun getWallpaperPath(): String? {
        val file = File(storageManager.getWallpaperDir(), "custom_wallpaper.jpg")
        return if (file.exists()) file.absolutePath else null
    }
}
